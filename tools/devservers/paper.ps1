param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("provision", "deploy-plugin", "sync-plugins", "run-server", "debug-server")]
    [string] $Action,

    [string] $MinecraftVersion = "1.21.8",
    [string] $ServerDirectory,
    [string] $PluginJar,
    [string] $PluginManifest,
    [string] $ReservedPluginFile,
    [string] $PaperJar,
    [string] $JavaExecutable
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$exampleRoot = Join-Path $repoRoot "examples/bukkit-permissions-example"
if ([string]::IsNullOrWhiteSpace($ServerDirectory)) {
    $ServerDirectory = Join-Path $exampleRoot "build/devserver/paper-$MinecraftVersion"
}
$ServerDirectory = [System.IO.Path]::GetFullPath($ServerDirectory)
$pluginsDirectory = Join-Path $ServerDirectory "plugins"
$serverJar = Join-Path $ServerDirectory "paper.jar"
$userAgent = "MinecraftDevKit-devserver/1.0 (https://github.com/TrickshotMLG/MinecraftDevKit)"

function Get-PaperDownload {
    $uri = "https://fill.papermc.io/v3/projects/paper/versions/$MinecraftVersion/builds"
    $builds = Invoke-RestMethod -Uri $uri -Headers @{ "User-Agent" = $userAgent }
    if ($builds.ok -eq $false) {
        throw "Paper downloads API rejected Minecraft $MinecraftVersion`: $($builds.message)"
    }

    $build = @($builds | Where-Object { $_.channel -eq "STABLE" } | Sort-Object id -Descending | Select-Object -First 1)
    if ($build.Count -eq 0 -or $null -eq $build[0].downloads.'server:default') {
        throw "No stable Paper build is available for Minecraft $MinecraftVersion."
    }
    return [PSCustomObject]@{
        BuildId = $build[0].id
        Download = $build[0].downloads.'server:default'
    }
}

function Install-Paper {
    New-Item -ItemType Directory -Path $ServerDirectory -Force | Out-Null
    $paperBuild = Get-PaperDownload
    $download = $paperBuild.Download
    $temporaryJar = "$serverJar.download"
    try {
        Invoke-WebRequest -Uri $download.url -Headers @{ "User-Agent" = $userAgent } -OutFile $temporaryJar
        $actualHash = (Get-FileHash -LiteralPath $temporaryJar -Algorithm SHA256).Hash.ToLowerInvariant()
        $expectedHash = $download.checksums.sha256.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) {
            throw "Paper JAR SHA-256 mismatch. Expected $expectedHash, got $actualHash."
        }
        Move-Item -LiteralPath $temporaryJar -Destination $serverJar -Force
    }
    finally {
        Remove-Item -LiteralPath $temporaryJar -Force -ErrorAction SilentlyContinue
    }

    $eulaFile = Join-Path $ServerDirectory "eula.txt"
    if (-not (Test-Path -LiteralPath $eulaFile)) {
        Set-Content -LiteralPath $eulaFile -Value "eula=false" -Encoding ascii
    }
    New-Item -ItemType Directory -Path $pluginsDirectory -Force | Out-Null
    Write-Host "Installed Paper $MinecraftVersion build $($paperBuild.BuildId) at $ServerDirectory"
    Write-Host "Review https://www.minecraft.net/eula and set eula=true in $eulaFile before starting the server."
}

function Sync-PluginManifest {
    if ([string]::IsNullOrWhiteSpace($PluginManifest)) {
        throw "-PluginManifest is required for sync-plugins."
    }
    $manifestPath = (Resolve-Path -LiteralPath $PluginManifest).Path
    $entries = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $names = @{}
    New-Item -ItemType Directory -Path $pluginsDirectory -Force | Out-Null
    foreach ($entry in $entries) {
        $downloadUri = $null
        if ([string]::IsNullOrWhiteSpace($entry.fileName) -or
            [System.IO.Path]::GetFileName($entry.fileName) -ne $entry.fileName -or
            $entry.fileName -notmatch '\.jar$' -or
            -not [System.Uri]::TryCreate($entry.url, [System.UriKind]::Absolute, [ref] $downloadUri) -or
            $downloadUri.Scheme -ne "https" -or
            $entry.sha256 -notmatch '^[A-Fa-f0-9]{64}$') {
            throw "Each plugin manifest entry requires a safe .jar fileName, HTTPS URL, and 64-character SHA-256."
        }
        if ($names.ContainsKey($entry.fileName)) {
            throw "The plugin manifest contains the duplicate file name '$($entry.fileName)'."
        }
        if (-not [string]::IsNullOrWhiteSpace($ReservedPluginFile) -and
            $entry.fileName.Equals($ReservedPluginFile, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "The plugin manifest file name '$($entry.fileName)' conflicts with the plugin deployed from this example build."
        }
        $names[$entry.fileName] = $true
        $destination = Join-Path $pluginsDirectory $entry.fileName
        if ((Test-Path -LiteralPath $destination) -and
            (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ieq $entry.sha256) {
            continue
        }
        $temporaryJar = "$destination.download"
        try {
            Invoke-WebRequest -Uri $entry.url -Headers @{ "User-Agent" = $userAgent } -OutFile $temporaryJar
            $actualHash = (Get-FileHash -LiteralPath $temporaryJar -Algorithm SHA256).Hash
            if ($actualHash -ine $entry.sha256) {
                throw "SHA-256 mismatch for $($entry.fileName). Expected $($entry.sha256), got $actualHash."
            }
            Move-Item -LiteralPath $temporaryJar -Destination $destination -Force
        }
        finally {
            Remove-Item -LiteralPath $temporaryJar -Force -ErrorAction SilentlyContinue
        }
    }
}

function Start-PaperServer([bool] $Debug) {
    $eulaFile = Join-Path $ServerDirectory "eula.txt"
    if (-not (Test-Path -LiteralPath $eulaFile) -or
        -not (Get-Content -LiteralPath $eulaFile | Where-Object { $_.Trim().Equals("eula=true", [System.StringComparison]::OrdinalIgnoreCase) })) {
        throw "Paper requires EULA acceptance. Review https://www.minecraft.net/eula, then set eula=true in $eulaFile."
    }
    if ([string]::IsNullOrWhiteSpace($JavaExecutable) -or -not (Test-Path -LiteralPath $JavaExecutable)) {
        throw "Java executable was not found: $JavaExecutable"
    }
    if ([string]::IsNullOrWhiteSpace($PaperJar) -or -not (Test-Path -LiteralPath $PaperJar)) {
        throw "Paper server JAR was not found: $PaperJar. Run provisionPaperServer first."
    }

    $javaArguments = @()
    if ($Debug) {
        $javaArguments += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005"
    }
    $javaArguments += @("-Xms1G", "-Xmx2G", "-jar", $PaperJar, "--nogui")
    Push-Location $ServerDirectory
    try {
        & $JavaExecutable @javaArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Paper exited with code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

switch ($Action) {
    "provision" { Install-Paper }
    "deploy-plugin" {
        if ([string]::IsNullOrWhiteSpace($PluginJar)) { throw "-PluginJar is required for deploy-plugin." }
        $sourceJar = (Resolve-Path -LiteralPath $PluginJar).Path
        New-Item -ItemType Directory -Path $pluginsDirectory -Force | Out-Null
        Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $pluginsDirectory (Split-Path $sourceJar -Leaf)) -Force
        Write-Host "Deployed $(Split-Path $sourceJar -Leaf) to $pluginsDirectory"
    }
    "sync-plugins" { Sync-PluginManifest }
    "run-server" { Start-PaperServer -Debug $false }
    "debug-server" { Start-PaperServer -Debug $true }
}
