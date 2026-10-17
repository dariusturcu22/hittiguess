<#
Copies the error-tracking, tracing, and voice-relay settings from the local .env files into the
hittiguess-core and hittiguess-ai Container Apps.

Core receives SENTRY_DSN, the OpenTelemetry endpoint and headers, and the Cloudflare TURN key id
and API token from backend\.env. The AI service receives SENTRY_DSN and the OpenTelemetry endpoint
and headers from ai\.env. Credentials become Container Apps secrets referenced by name; the
OpenTelemetry endpoint and the switch that enables tracing are plain settings. Values are read from
the files and sent to Azure; they are never printed. Only the names are listed.

The update adds or replaces just these settings, so every other environment variable, secret, and
resource limit stays as it is. The script waits for a healthy new revision and removes the settings
it added when none appears. Running it again replaces the values, so it is safe to repeat.

Run it yourself in a PowerShell window where `az login` is already done.

Usage: .\scripts\azure\set-observability-secrets.ps1 [-DryRun] [-AppNames hittiguess-core]
  -DryRun  lists which settings would be sent for each app, and which are missing or empty in the
           .env file, without sending anything.
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = "hittiguess-rg",
    [string[]]$AppNames = @("hittiguess-core", "hittiguess-ai"),
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$HealthPollSeconds = 8
$HealthPollAttempts = 30
$SafeValuePattern = '^[A-Za-z0-9_.~=+/:@%,-]+$'

# Each entry: the variable name, the Container Apps secret name (empty for a plain setting), and the
# .env file it is read from. Plain settings with a fixed value use Fixed instead of reading a file.
$AppSettings = @{
    "hittiguess-core" = @{
        EnvFile  = "backend\.env"
        Settings = @(
            @{ Name = "SENTRY_DSN"; Secret = "sentry-dsn" },
            @{ Name = "OTEL_EXPORTER_OTLP_ENDPOINT"; Secret = "" },
            @{ Name = "OTEL_EXPORTER_OTLP_HEADERS"; Secret = "otel-exporter-otlp-headers" },
            @{ Name = "OTEL_SDK_DISABLED"; Secret = ""; Fixed = "false"; RequiresAll = @("OTEL_EXPORTER_OTLP_ENDPOINT", "OTEL_EXPORTER_OTLP_HEADERS") },
            @{ Name = "VOICE_TURN_CLOUDFLARE_KEY_ID"; Secret = "voice-turn-cloudflare-key-id" },
            @{ Name = "VOICE_TURN_CLOUDFLARE_API_TOKEN"; Secret = "voice-turn-cloudflare-api-token" }
        )
    }
    "hittiguess-ai" = @{
        EnvFile  = "ai\.env"
        Settings = @(
            @{ Name = "SENTRY_DSN"; Secret = "sentry-dsn" },
            @{ Name = "OTEL_EXPORTER_OTLP_ENDPOINT"; Secret = "" },
            @{ Name = "OTEL_EXPORTER_OTLP_HEADERS"; Secret = "otel-exporter-otlp-headers" }
        )
    }
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

function Read-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { throw "$Path was not found." }
    $values = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $value = $Matches[2]
            if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) { $value = $value.Substring(1, $value.Length - 2) }
            elseif ($value.Length -ge 2 -and $value.StartsWith("'") -and $value.EndsWith("'")) { $value = $value.Substring(1, $value.Length - 2) }
            $values[$Matches[1]] = $value
        }
    }
    return $values
}

function Resolve-Settings {
    param($App, [hashtable]$EnvValues)
    $resolved = @()
    $missing = @()
    foreach ($setting in $App.Settings) {
        if ($setting.ContainsKey("Fixed")) { continue }
        $value = $EnvValues[$setting.Name]
        if ([string]::IsNullOrWhiteSpace($value)) { $missing += $setting.Name; continue }
        if ($value -notmatch $SafeValuePattern) {
            throw "$($setting.Name) contains characters this script does not pass to the Azure CLI. Set it in the Azure portal instead."
        }
        $resolved += @{ Name = $setting.Name; Secret = $setting.Secret; Value = $value }
    }
    foreach ($setting in $App.Settings) {
        if (-not $setting.ContainsKey("Fixed")) { continue }
        $available = $resolved | ForEach-Object { $_.Name }
        $ready = $true
        foreach ($required in $setting.RequiresAll) { if ($available -notcontains $required) { $ready = $false } }
        if ($ready) { $resolved += @{ Name = $setting.Name; Secret = ""; Value = $setting.Fixed } } else { $missing += $setting.Name }
    }
    # The TURN key id and token only work together.
    $turnNames = @("VOICE_TURN_CLOUDFLARE_KEY_ID", "VOICE_TURN_CLOUDFLARE_API_TOKEN")
    $turnPresent = @($resolved | Where-Object { $turnNames -contains $_.Name })
    if ($turnPresent.Count -eq 1) {
        $resolved = @($resolved | Where-Object { $turnNames -notcontains $_.Name })
        $missing += $turnNames
    }
    return [pscustomobject]@{ Resolved = $resolved; Missing = $missing }
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

function Set-AppSettings {
    param([string]$AppName, $Resolved)
    $secretArguments = @($Resolved | Where-Object { $_.Secret } | ForEach-Object { "$($_.Secret)=$($_.Value)" })
    if ($secretArguments.Count -gt 0) {
        $result = Invoke-AzureCapture (@("containerapp", "secret", "set", "--name", $AppName, "--resource-group", $ResourceGroup, "--secrets") + $secretArguments + @("-o", "none"))
        if ($result.ExitCode -ne 0) { throw "Setting the secrets on $AppName failed: $($result.Error)" }
        # The Azure CLI is a batch wrapper on Windows, so confirm each stored secret equals the value read from the file before any app uses it.
        foreach ($item in ($Resolved | Where-Object { $_.Secret })) {
            $stored = Invoke-AzureCapture @("containerapp", "secret", "show", "--name", $AppName, "--resource-group", $ResourceGroup, "--secret-name", $item.Secret, "--query", "value", "-o", "tsv")
            if ($stored.ExitCode -ne 0 -or $stored.Output -ne $item.Value) { throw "The stored secret $($item.Secret) on $AppName does not match the value in the .env file. Set it in the Azure portal instead." }
        }
    }
    $envArguments = @($Resolved | ForEach-Object { if ($_.Secret) { "$($_.Name)=secretref:$($_.Secret)" } else { "$($_.Name)=$($_.Value)" } })
    $result = Invoke-AzureCapture (@("containerapp", "update", "--name", $AppName, "--resource-group", $ResourceGroup, "--set-env-vars") + $envArguments + @("-o", "none"))
    if ($result.ExitCode -ne 0) { throw "Updating $AppName failed: $($result.Error)" }
}

# Allows dot-sourcing this file to load the functions without changing anything.
if ($MyInvocation.InvocationName -eq ".") { return }

$script:AzureCli = Get-AzureCli
$account = Invoke-AzureCapture @("account", "show", "--query", "name", "-o", "tsv")
if ($account.ExitCode -ne 0) { throw "Not signed in. Run: az login" }
Write-Host "Subscription: $($account.Output), resource group: $ResourceGroup$(if ($DryRun) { ' (dry run, nothing will be sent)' })"

$plans = @()
foreach ($appName in $AppNames) {
    if (-not $AppSettings.ContainsKey($appName)) { throw "No settings defined for $appName." }
    $app = $AppSettings[$appName]
    $envValues = Read-EnvFile (Join-Path $RepositoryRoot $app.EnvFile)
    $plan = Resolve-Settings $app $envValues
    Write-Host "`n$appName (from $($app.EnvFile))"
    foreach ($item in $plan.Resolved) { Write-Host "  will set  $($item.Name)$(if ($item.Secret) { ' (secret)' })" }
    foreach ($name in $plan.Missing) { Write-Host "  skipped   $name (empty or missing in $($app.EnvFile))" }
    if ($plan.Resolved.Count -eq 0) { Write-Host "  nothing to send"; continue }
    $plans += [pscustomobject]@{ AppName = $appName; Resolved = $plan.Resolved }
}
if ($DryRun -or $plans.Count -eq 0) { return }

if ((Read-Host "`nSend these settings to Azure? (y/N)") -ne "y") { return }

foreach ($plan in $plans) {
    $appName = $plan.AppName
    Write-Host "`nUpdating $appName"
    $existing = Invoke-AzureCapture @("containerapp", "show", "--name", $appName, "--resource-group", $ResourceGroup, "--query", "properties.template.containers[0].env[].name", "-o", "tsv")
    if ($existing.ExitCode -ne 0) { throw "Could not read $appName. Run 'az login' if the session expired. Details: $($existing.Error)" }
    $existingNames = @($existing.Output -split "\s+" | Where-Object { $_ })
    $addedNames = @($plan.Resolved | ForEach-Object { $_.Name } | Where-Object { $existingNames -notcontains $_ })

    $previousRevisionName = ((Get-ServingState $appName) -split " ")[0]
    Set-AppSettings $appName $plan.Resolved
    if (Wait-HealthyNewRevision $appName $previousRevisionName) { continue }

    Write-Warning "  removing the settings that were added to $appName"
    if ($addedNames.Count -gt 0) {
        $result = Invoke-AzureCapture (@("containerapp", "update", "--name", $appName, "--resource-group", $ResourceGroup, "--remove-env-vars") + $addedNames + @("-o", "none"))
        if ($result.ExitCode -ne 0) { throw "Removing the added settings from $appName failed: $($result.Error)" }
    }
    if (-not (Wait-HealthyNewRevision $appName $previousRevisionName)) { throw "$appName is still not healthy after the removal. Check the revisions in the Azure portal." }
    throw "$appName did not become healthy with the new settings; the added settings were removed."
}
Write-Host "`nDone. Verify with: az containerapp show --name <app> --resource-group $ResourceGroup --query properties.template.containers[0].env[].name"
