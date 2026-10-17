<#
Adds startup, liveness, and readiness HTTP probes to the hittiguess-ai and hittiguess-core
Container Apps.

A Container Apps YAML update replaces the whole container definition, so a partial file drops
the environment variables, secret references, and resource limits. This script therefore reads
the running container's definition from Azure, sends it back unchanged with the probes added,
checks that no environment variable was lost, waits for the new revision to become healthy, and
restores the previous definition (without probes) when it does not. Running it again replaces the
probes with the values below, so it is safe to repeat. Nothing secret is printed or written: the
definition holds secret references, never secret values.

Run it yourself in a PowerShell window where `az login` is already done.

Usage: .\scripts\azure\apply-health-probes.ps1 [-DryRun] [-NoWait] [-AppNames hittiguess-core]
  -DryRun  builds and checks the update for each app without sending it.
  -NoWait  sends the update without waiting for a healthy revision, for apps that scale to zero and
           have no running replica yet (the creation script uses it).#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = "hittiguess-rg",
    [string[]]$AppNames = @("hittiguess-ai", "hittiguess-core"),
    [switch]$DryRun,
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"

$ProbeTimeoutSeconds = 5
$LivenessPeriodSeconds = 20
$ReadinessPeriodSeconds = 10
$FailureThreshold = 3
$StartupFailureThreshold = 10
$HealthPollSeconds = 8
$HealthPollAttempts = 30

# Startup allows initialDelay + period * failureThreshold seconds before Azure gives up on a start.
$ProbeSettings = @{
    "hittiguess-ai"   = @{ Port = 8000; StartupPath = "/health"; LivenessPath = "/health"; ReadinessPath = "/health"; StartupDelaySeconds = 5; StartupPeriodSeconds = 5 }
    "hittiguess-core" = @{ Port = 8080; StartupPath = "/actuator/health/readiness"; LivenessPath = "/actuator/health/liveness"; ReadinessPath = "/actuator/health/readiness"; StartupDelaySeconds = 10; StartupPeriodSeconds = 10 }
}

function Get-AzureCli {
    $command = Get-Command az.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $installed = "C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin\az.cmd"
    if (Test-Path $installed) { return $installed }
    throw "The Azure CLI was not found. Install it with: winget install -e --id Microsoft.AzureCLI"
}

function Invoke-AzureCapture {
    param([string[]]$Arguments)
    $errorFile = [IO.Path]::GetTempFileName()
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:AzureCli @Arguments --only-show-errors 2>$errorFile
        $exitCode = $LASTEXITCODE
        $errorText = (Get-Content -Raw -Path $errorFile -ErrorAction SilentlyContinue)
        return [pscustomobject]@{ ExitCode = $exitCode; Output = (($output | Out-String).Trim()); Error = "$errorText".Trim() }
    }
    finally {
        $ErrorActionPreference = $previousPreference
        Remove-Item -Path $errorFile -ErrorAction SilentlyContinue
    }
}

function Get-LiveContainer {
    param([string]$AppName)
    $result = Invoke-AzureCapture @("containerapp", "show", "--name", $AppName, "--resource-group", $ResourceGroup,
        "--query", "properties.template.containers[0]", "-o", "json")
    if ($result.ExitCode -ne 0) { throw "Could not read $AppName. Run 'az login' if the session expired. Details: $($result.Error)" }
    return $result.Output | ConvertFrom-Json
}

function New-ProbeProperty {
    param([string]$Kind, [string]$Path, [int]$Port, [hashtable]$Timing)
    $probe = [ordered]@{ type = $Kind; httpGet = [ordered]@{ path = $Path; port = $Port }; timeoutSeconds = $ProbeTimeoutSeconds }
    foreach ($key in $Timing.Keys) { $probe[$key] = $Timing[$key] }
    return $probe
}

function New-ContainerUpdate {
    param($Container, [string]$AppName, [bool]$WithProbes)
    $definition = [ordered]@{
        name      = $Container.name
        image     = $Container.image
        env       = @($Container.env)
        resources = $Container.resources
    }
    if ($Container.command) { $definition["command"] = @($Container.command) }
    if ($Container.args) { $definition["args"] = @($Container.args) }
    if ($WithProbes) {
        $settings = $ProbeSettings[$AppName]
        $definition["probes"] = @(
            (New-ProbeProperty "Startup" $settings.StartupPath $settings.Port @{ initialDelaySeconds = $settings.StartupDelaySeconds; periodSeconds = $settings.StartupPeriodSeconds; failureThreshold = $StartupFailureThreshold }),
            (New-ProbeProperty "Liveness" $settings.LivenessPath $settings.Port @{ periodSeconds = $LivenessPeriodSeconds; failureThreshold = $FailureThreshold }),
            (New-ProbeProperty "Readiness" $settings.ReadinessPath $settings.Port @{ periodSeconds = $ReadinessPeriodSeconds; failureThreshold = $FailureThreshold })
        )
    }
    return [ordered]@{ properties = [ordered]@{ template = [ordered]@{ containers = @($definition) } } }
}

function Write-UpdateFile {
    param($Update)
    $path = [IO.Path]::Combine([IO.Path]::GetTempPath(), "containerapp-$([guid]::NewGuid().ToString('N')).json")
    [IO.File]::WriteAllText($path, ($Update | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding $false))
    return $path
}

function Get-ServingState {
    param([string]$AppName)
    $query = '[?properties.trafficWeight > `0`] | [0].join('' '', [name, properties.healthState, properties.runningState, to_string(properties.trafficWeight)])'
    $result = Invoke-AzureCapture @("containerapp", "revision", "list", "--name", $AppName, "--resource-group", $ResourceGroup, "--query", $query, "-o", "tsv")
    return $result.Output
}

function Wait-HealthyNewRevision {
    param([string]$AppName, [string]$PreviousRevision)
    $state = ""
    for ($attempt = 1; $attempt -le $HealthPollAttempts; $attempt++) {
        $state = Get-ServingState $AppName
        $revision = ($state -split " ")[0]
        if ($state -match " Healthy " -and $revision -ne $PreviousRevision) {
            Write-Host "  $AppName is serving: $state"
            return $true
        }
        Start-Sleep -Seconds $HealthPollSeconds
    }
    Write-Warning "  $AppName did not become healthy on a new revision. Last state: $state"
    return $false
}

function Submit-ContainerUpdate {
    param([string]$AppName, $Update)
    $path = Write-UpdateFile $Update
    try {
        $result = Invoke-AzureCapture @("containerapp", "update", "--name", $AppName, "--resource-group", $ResourceGroup, "--yaml", $path, "-o", "none")
        if ($result.ExitCode -ne 0) { throw "Updating $AppName failed: $($result.Error)" }
    }
    finally { Remove-Item -Path $path -ErrorAction SilentlyContinue }
}

# Allows dot-sourcing this file to load the functions without changing anything.
if ($MyInvocation.InvocationName -eq ".") { return }

$script:AzureCli = Get-AzureCli
$account = Invoke-AzureCapture @("account", "show", "--query", "name", "-o", "tsv")
if ($account.ExitCode -ne 0) { throw "Not signed in. Run: az login" }
Write-Host "Subscription: $($account.Output), resource group: $ResourceGroup$(if ($DryRun) { ' (dry run, nothing will be sent)' })"

foreach ($appName in $AppNames) {
    if (-not $ProbeSettings.ContainsKey($appName)) { throw "No probe settings for $appName." }
    Write-Host "`n$appName"
    $container = Get-LiveContainer $appName
    $liveEnvCount = @($container.env).Count
    $update = New-ContainerUpdate $container $appName $true
    $updateEnvCount = @($update.properties.template.containers[0].env).Count
    if ($updateEnvCount -ne $liveEnvCount) { throw "$appName lost environment variables in the update ($updateEnvCount of $liveEnvCount). Not applying." }
    Write-Host "  live definition read: $liveEnvCount environment variables, cpu $($container.resources.cpu), memory $($container.resources.memory)"
    if ($DryRun) { Write-Host "  dry run: would set probes Startup, Liveness, Readiness"; continue }

    $previousRevision = Get-ServingState $appName
    $previousRevisionName = ($previousRevision -split " ")[0]
    Submit-ContainerUpdate $appName $update
    if ($NoWait) { Write-Host "  probes sent; not waiting for a healthy revision"; continue }
    if (-not (Wait-HealthyNewRevision $appName $previousRevisionName)) {
        Write-Warning "  restoring $appName without probes"
        Submit-ContainerUpdate $appName (New-ContainerUpdate $container $appName $false)
        if (-not (Wait-HealthyNewRevision $appName $previousRevisionName)) { throw "$appName is still not healthy after the restore. Check the revisions in the Azure portal." }
        throw "$appName did not accept the probes; it was restored without them."
    }
}
Write-Host "`nDone. Verify with: az containerapp show --name <app> --resource-group $ResourceGroup --query properties.template.containers[0].probes[].type"
