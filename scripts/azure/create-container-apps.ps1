<#
Creates the hittiguess-ai and hittiguess-core Container Apps in the existing resource group
and environment, with every secret stored as a Container Apps secret.

Run it yourself in a PowerShell window where `az login` is already done. It prompts for each
secret with hidden input, generates the JWT signing secret and the internal service key
locally, and never prints a secret. Prompted values are validated against a conservative
character set because the Azure CLI is a batch wrapper on Windows.

Neon connection strings must be the DIRECT connection (the host without "-pooler"), since
Flyway and the connection pool need session-level features.

Usage: .\scripts\azure\create-container-apps.ps1
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = "hittiguess-rg",
    [string]$EnvironmentName = "hittiguess-env",
    [string]$ImageOwner = "dariusturcu22",
    [string]$FrontendUrl = "https://www.hittiguess.com",
    [string]$FrontendAllowedOrigins = "https://www.hittiguess.com,https://hittiguess.com",
    [string]$CookieDomain = "hittiguess.com",
    [string]$EmailFromAddress = "onboarding@resend.dev"
)

$ErrorActionPreference = "Stop"

$AiAppName = "hittiguess-ai"
$CoreAppName = "hittiguess-core"
$AiPort = 8000
$CorePort = 8080
$AiCpu = "0.5"
$AiMemory = "1.0Gi"
$CoreCpu = "1.0"
$CoreMemory = "2.0Gi"
$MinReplicas = 0
$MaxReplicas = 1
$GeneratedSecretByteCount = 48
$PooledHostMarker = "-pooler"
$DefaultPostgresPort = 5432
$SafeValuePattern = '^[A-Za-z0-9_.~=+/:@?-]+$'
$SafePasswordPattern = '^[A-Za-z0-9_.~-]+$'
$NotFoundPattern = 'ResourceNotFound|ResourceGroupNotFound|was not found|could not be found|does not exist'

function Get-AzureCli {
    $command = Get-Command az.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $installed = "C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin\az.cmd"
    if (Test-Path $installed) { return $installed }
    throw "The Azure CLI was not found. Install it with: winget install -e --id Microsoft.AzureCLI"
}

function Invoke-AzureCapture {
    param([string]$AzureCli, [string[]]$Arguments)
    $errorFile = [IO.Path]::GetTempFileName()
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $AzureCli @Arguments --only-show-errors 2>$errorFile
        $exitCode = $LASTEXITCODE
        $errorText = (Get-Content -Raw -Path $errorFile -ErrorAction SilentlyContinue)
        return [pscustomobject]@{ ExitCode = $exitCode; Output = (($output | Out-String).Trim()); Error = "$errorText".Trim() }
    }
    finally {
        $ErrorActionPreference = $previousPreference
        Remove-Item -Path $errorFile -ErrorAction SilentlyContinue
    }
}

function Test-ContainerAppExists {
    param([string]$AzureCli, [string]$Name, [string]$ResourceGroupName)
    $result = Invoke-AzureCapture $AzureCli @("containerapp", "show", "--name", $Name, "--resource-group", $ResourceGroupName, "--query", "name", "-o", "tsv")
    if ($result.ExitCode -eq 0) { return $true }
    if ($result.Error -match $NotFoundPattern) { return $false }
    throw "Could not check whether $Name exists: $($result.Error)"
}

function Read-SecretText {
    param([string]$Prompt)
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $text = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ([string]::IsNullOrWhiteSpace($text)) { throw "$Prompt was empty." }
    if ($text -notmatch $SafeValuePattern) {
        throw "$Prompt contains characters this script does not pass to the Azure CLI. Regenerate it or set it in the Azure portal."
    }
    return $text.Trim()
}

function New-RandomSecret {
    $bytes = New-Object byte[] $GeneratedSecretByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return ([Convert]::ToBase64String($bytes)).Replace("+", "-").Replace("/", "_").TrimEnd("=")
}

function ConvertFrom-NeonUrl {
    param([string]$ConnectionString, [string]$Label)
    $uri = $null
    if (-not [uri]::TryCreate($ConnectionString, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -notin @("postgresql", "postgres")) {
        throw "$Label is not a postgresql:// connection string."
    }
    if ($uri.Host.Contains($PooledHostMarker)) {
        throw "$Label uses the pooled connection. Copy the direct connection (the host without '-pooler') from the Neon console."
    }
    $userParts = $uri.UserInfo.Split(":", 2)
    if ($userParts.Count -ne 2) { throw "$Label has no user and password." }
    $username = [uri]::UnescapeDataString($userParts[0])
    $password = [uri]::UnescapeDataString($userParts[1])
    if ($password -notmatch $SafePasswordPattern) {
        throw "$Label has a password with characters this script cannot pass safely. Reset the password in Neon to get a simpler one."
    }
    $port = if ($uri.Port -gt 0) { $uri.Port } else { $DefaultPostgresPort }
    $database = $uri.AbsolutePath.TrimStart("/")
    return [pscustomobject]@{
        JdbcUrl  = "jdbc:postgresql://$($uri.Host):$port/${database}?sslmode=require"
        Url      = "postgresql://${username}:${password}@$($uri.Host):$port/${database}?sslmode=require"
        Username = $username
        Password = $password
    }
}

# Allows dot-sourcing this file to load the functions without creating anything.
if ($MyInvocation.InvocationName -eq ".") { return }

$az = Get-AzureCli

function Invoke-Az {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $az @args --only-show-errors
        if ($LASTEXITCODE -ne 0) { throw "az $($args[0..1] -join ' ') failed." }
    }
    finally { $ErrorActionPreference = $previousPreference }
}

$account = Invoke-AzureCapture $az @("account", "show", "--query", "name", "-o", "tsv")
if ($account.ExitCode -ne 0) { throw "Not signed in. Run: az login" }
$session = Invoke-AzureCapture $az @("group", "show", "--name", $ResourceGroup, "--query", "name", "-o", "tsv")
if ($session.ExitCode -ne 0) {
    throw "Azure rejected the request, usually an expired login. Run 'az login', then run this script again. Details: $($session.Error)"
}
Write-Host "Subscription: $($account.Output)"
Write-Host "Resource group: $ResourceGroup, environment: $EnvironmentName"
if ((Read-Host "Create the two Container Apps here? (y/N)") -ne "y") { return }

foreach ($appName in @($AiAppName, $CoreAppName)) {
    if (Test-ContainerAppExists $az $appName $ResourceGroup) {
        throw "$appName already exists. This script only creates; update it with 'az containerapp update'."
    }
}

Write-Host "`nPaste each value when prompted. Input is hidden."
$coreDatabase = ConvertFrom-NeonUrl (Read-SecretText "Neon DIRECT connection string for hittiguess-core") "The core connection string"
$analyticsDatabase = ConvertFrom-NeonUrl (Read-SecretText "Neon DIRECT connection string for hittiguess-analytics") "The analytics connection string"
$openAiKey = Read-SecretText "OpenAI API key"
$deepInfraKey = Read-SecretText "DeepInfra API key"
$youTubeKey = Read-SecretText "YouTube Data API key"
$discogsKey = Read-SecretText "Discogs consumer key"
$discogsSecret = Read-SecretText "Discogs consumer secret"
$googleClientId = Read-SecretText "Google OAuth client ID"
$googleClientSecret = Read-SecretText "Google OAuth client secret"
$resendKey = Read-SecretText "Resend API key"
$jwtSecret = New-RandomSecret
$internalServiceKey = New-RandomSecret

Write-Host "`nCreating $AiAppName (internal ingress only)..."
Invoke-Az containerapp create --name $AiAppName --resource-group $ResourceGroup --environment $EnvironmentName `
    --image "ghcr.io/$ImageOwner/hittiguess-ai:latest" `
    --ingress internal --target-port $AiPort `
    --cpu $AiCpu --memory $AiMemory --min-replicas $MinReplicas --max-replicas $MaxReplicas `
    --secrets "openai-api-key=$openAiKey" "deepinfra-api-key=$deepInfraKey" "youtube-api-key=$youTubeKey" `
        "discogs-consumer-key=$discogsKey" "discogs-consumer-secret=$discogsSecret" `
        "internal-service-api-key=$internalServiceKey" "database-url=$($coreDatabase.Url)" `
    --env-vars "OPENAI_API_KEY=secretref:openai-api-key" "DEEPINFRA_API_KEY=secretref:deepinfra-api-key" `
        "YOUTUBE_API_KEY=secretref:youtube-api-key" "DISCOGS_CONSUMER_KEY=secretref:discogs-consumer-key" `
        "DISCOGS_CONSUMER_SECRET=secretref:discogs-consumer-secret" `
        "INTERNAL_SERVICE_API_KEY=secretref:internal-service-api-key" "DATABASE_URL=secretref:database-url" `
    -o none

$aiAddress = Invoke-AzureCapture $az @("containerapp", "show", "--name", $AiAppName, "--resource-group", $ResourceGroup, "--query", "properties.configuration.ingress.fqdn", "-o", "tsv")
$aiHost = $aiAddress.Output
if ($aiAddress.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($aiHost)) { throw "Could not read the AI app's internal address." }

Write-Host "Creating $CoreAppName (external HTTPS and WebSocket ingress)..."
Invoke-Az containerapp create --name $CoreAppName --resource-group $ResourceGroup --environment $EnvironmentName `
    --image "ghcr.io/$ImageOwner/hittiguess-core:latest" `
    --ingress external --target-port $CorePort `
    --cpu $CoreCpu --memory $CoreMemory --min-replicas $MinReplicas --max-replicas $MaxReplicas `
    --secrets "db-url=$($coreDatabase.JdbcUrl)" "db-username=$($coreDatabase.Username)" "db-password=$($coreDatabase.Password)" `
        "analytics-db-url=$($analyticsDatabase.JdbcUrl)" "analytics-db-username=$($analyticsDatabase.Username)" `
        "analytics-db-password=$($analyticsDatabase.Password)" `
        "jwt-secret=$jwtSecret" "internal-service-api-key=$internalServiceKey" `
        "oauth2-client-id=$googleClientId" "oauth2-client-secret=$googleClientSecret" "resend-api-key=$resendKey" `
    --env-vars "APP_ENV=prod" "FRONTEND_URL=$FrontendUrl" "FRONTEND_ALLOWED_ORIGINS=$FrontendAllowedOrigins" `
        "COOKIE_DOMAIN=$CookieDomain" "EMAIL_FROM_ADDRESS=$EmailFromAddress" "AI_SERVICE_URL=https://$aiHost" `
        "DB_URL=secretref:db-url" "DB_USERNAME=secretref:db-username" "DB_PASSWORD=secretref:db-password" `
        "ANALYTICS_DB_URL=secretref:analytics-db-url" "ANALYTICS_DB_USERNAME=secretref:analytics-db-username" `
        "ANALYTICS_DB_PASSWORD=secretref:analytics-db-password" `
        "JWT_SECRET=secretref:jwt-secret" "INTERNAL_SERVICE_API_KEY=secretref:internal-service-api-key" `
        "OAUTH2_CLIENT_ID=secretref:oauth2-client-id" "OAUTH2_CLIENT_SECRET=secretref:oauth2-client-secret" `
        "RESEND_API_KEY=secretref:resend-api-key" `
    -o none

$coreHost = (Invoke-AzureCapture $az @("containerapp", "show", "--name", $CoreAppName, "--resource-group", $ResourceGroup, "--query", "properties.configuration.ingress.fqdn", "-o", "tsv")).Output
Write-Host "`nDone. Secrets were stored in Container Apps and not printed."
Write-Host "Core app address: https://$coreHost"
Write-Host "AI app address (internal only): https://$aiHost"
Write-Host "Check the core app: https://$coreHost/actuator/health/readiness"
