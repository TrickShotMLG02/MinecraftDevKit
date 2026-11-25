param(
    [ValidateSet("spigot", "paper", "all")]
    [string] $Platform = "all",
    [string] $SpigotServerJar = $env:MDK_SPIGOT_SERVER_JAR,
    [string] $PaperServerJar = $env:MDK_PAPER_SERVER_JAR,
    [string] $SpigotEulaFile = $env:MDK_SPIGOT_EULA_FILE,
    [string] $PaperEulaFile = $env:MDK_PAPER_EULA_FILE,
    [string] $JavaExecutable = "java",
    [ValidateRange(30, 900)] [int] $StartupTimeoutSeconds = 240,
    [ValidateRange(5, 180)] [int] $ShutdownTimeoutSeconds = 45,
    [switch] $SkipBuild,
    [switch] $Provision
)

$ErrorActionPreference = "Stop"
$ExpectedMinecraft = "1.21.11"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$gradle = Join-Path $repoRoot "gradlew.bat"
$runId = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$baseResults = Join-Path $repoRoot "build/runtime-acceptance"
$platforms = if ($Platform -eq "all") { @("spigot", "paper") } else { @($Platform) }
$overallIncomplete = $false
$provisionRoot = Join-Path $baseResults "provision"
$knownSpigotSha256 = "da8b3f6e150d2da80e8a25a230c0344494a36001aa6ed2e8e527dfae3c58db5f"
$knownPaperSha256 = "5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba"

function Get-Sha256([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-ArtifactHash([string] $Path, [string] $ExpectedHash) {
    $actual = Get-Sha256 $Path
    return [bool]($actual -and $ExpectedHash -and $actual.Equals($ExpectedHash, [StringComparison]::OrdinalIgnoreCase))
}

function Assert-Java21([string] $Executable) {
    $command = Get-Command $Executable -ErrorAction SilentlyContinue
    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf) -and -not $command) { throw "Java executable not found: $Executable" }
    $info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = $Executable; $info.Arguments = "-version"
    $info.UseShellExecute = $false; $info.RedirectStandardError = $true; $info.RedirectStandardOutput = $true
    $proc = [Diagnostics.Process]::Start($info)
    try {
        $text = $proc.StandardError.ReadToEnd() + $proc.StandardOutput.ReadToEnd(); $proc.WaitForExit()
        if ($proc.ExitCode -ne 0 -or $text -notmatch 'version "21(?:\.|"|\+)') { throw "Java 21 is required for server provisioning. Found: $text" }
        return (($text -split "`r?`n")[0])
    } finally { $proc.Dispose() }
}

function Invoke-VerifiedDownload([string] $Uri, [string] $Destination, [string] $ExpectedHash = "") {
    $partial = "$Destination.download"
    try {
        if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
        Invoke-WebRequest -Uri $Uri -OutFile $partial -UseBasicParsing -TimeoutSec 180
        if (-not (Test-Path -LiteralPath $partial -PathType Leaf) -or (Get-Item -LiteralPath $partial).Length -lt 1024) { throw "Download was empty or incomplete: $Uri" }
        $actual = Get-Sha256 $partial
        if ($ExpectedHash -and $actual -ne $ExpectedHash.ToLowerInvariant()) { throw "SHA-256 mismatch for $Uri (expected $ExpectedHash; got $actual)." }
        Move-Item -LiteralPath $partial -Destination $Destination -Force
        return $actual
    } catch {
        Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
        throw "Official artifact download failed ($Uri): $($_.Exception.Message)"
    }
}

function Get-OrProvisionSpigot {
    $directory = Join-Path $provisionRoot "spigot"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $jar = Join-Path $directory "spigot-1.21.11.jar"
    $buildLog = Join-Path $directory "BuildTools.log.txt"
    if (Test-ArtifactHash $jar $knownSpigotSha256) {
        $provenance = Join-Path $directory "provenance.json"
        if (-not (Test-Path -LiteralPath $provenance)) {
            Set-Content -LiteralPath $provenance -Encoding utf8 -Value (@{ source="official SpigotMC BuildTools"; buildToolsBuild=202; minecraftVersion=$ExpectedMinecraft; spigotBuild=4598; sha256=$knownSpigotSha256; log=$buildLog } | ConvertTo-Json)
        }
        return [pscustomobject]@{ jar = $jar; eula = (Join-Path $directory "eula.txt"); source = "cached official BuildTools #202 output"; build = "Spigot 1.21.11 build 4598"; sha256 = $knownSpigotSha256 }
    }
    $null = Assert-Java21 $JavaExecutable
    $buildTools = Join-Path $directory "BuildTools.jar"
    if (-not (Test-Path -LiteralPath $buildTools) -or (Get-Item -LiteralPath $buildTools).Length -lt 1000000) {
        Invoke-VerifiedDownload "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar" $buildTools | Out-Null
    }
    $gitExe = Get-Command git -ErrorAction SilentlyContinue
    if (-not $gitExe) { throw "Official Spigot BuildTools requires Git on PATH. Install Git for Windows and retry." }
    Push-Location $directory
    try {
        $oldGit = $env:PATH
        try {
            & $JavaExecutable -jar $buildTools --rev $ExpectedMinecraft *>&1 | Tee-Object -FilePath $buildLog
            if ($LASTEXITCODE -ne 0) { throw "Spigot BuildTools failed with exit code $LASTEXITCODE. See $buildLog" }
        } finally { $env:PATH = $oldGit }
    } finally { Pop-Location }
    $built = Get-ChildItem -LiteralPath $directory -Filter "spigot-1.21.11.jar" -Recurse -File | Select-Object -First 1
    if (-not $built) { throw "BuildTools completed without producing Spigot $ExpectedMinecraft. See $buildLog" }
    if ($built.FullName -ne $jar) { Copy-Item -LiteralPath $built.FullName -Destination $jar -Force }
    $hash = Get-Sha256 $jar
    if (-not $hash) { throw "Spigot artifact hash could not be computed." }
    Set-Content -LiteralPath (Join-Path $directory "provenance.json") -Encoding utf8 -Value (@{ minecraftVersion=$ExpectedMinecraft; buildTools=(Get-Item $buildTools).VersionInfo.FileVersion; build="Spigot $ExpectedMinecraft (BuildTools output)"; sha256=$hash; log=$buildLog } | ConvertTo-Json)
    return [pscustomobject]@{ jar = $jar; eula = (Join-Path $directory "eula.txt"); source = "official Spigot BuildTools"; build = "Spigot $ExpectedMinecraft"; sha256 = $hash }
}

function Get-OrProvisionPaper {
    $directory = Join-Path $provisionRoot "paper"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $jar = Join-Path $directory "paper.jar"
    if (Test-ArtifactHash $jar $knownPaperSha256) {
        $provenance = Join-Path $directory "provenance.json"
        if (-not (Test-Path -LiteralPath $provenance)) {
            Set-Content -LiteralPath $provenance -Encoding utf8 -Value (@{ source="official PaperMC Downloads Service"; minecraftVersion=$ExpectedMinecraft; build=132; sha256=$knownPaperSha256; publishedSha256=$knownPaperSha256 } | ConvertTo-Json)
        }
        return [pscustomobject]@{ jar=$jar; eula=(Join-Path $directory "eula.txt"); source="cached PaperMC official build 132"; build="Paper 1.21.11 build 132"; sha256=$knownPaperSha256 }
    }
    $headers = @{ "User-Agent" = "MinecraftDevKit-runtime-acceptance (https://github.com/TrickShotMLG02/MinecraftDevKit)" }
    $builds = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/paper/versions/$ExpectedMinecraft/builds" -TimeoutSec 30
    $build = @($builds | Where-Object { $_.channel -eq "STABLE" -or $_.channel -eq "RECOMMENDED" } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)[0]
    if (-not $build) { throw "PaperMC API reports no stable Paper $ExpectedMinecraft builds." }
    $download = $build.downloads."server:default"
    if (-not $download.url -or -not $download.checksums.sha256) { throw "PaperMC metadata for build $($build.id) omitted its official URL or SHA-256." }
    $hash = Invoke-VerifiedDownload $download.url $jar $download.checksums.sha256
    Set-Content -LiteralPath (Join-Path $directory "provenance.json") -Encoding utf8 -Value (@{ minecraftVersion=$ExpectedMinecraft; build=$build.id; url=$download.url; sha256=$hash; publishedSha256=$download.checksums.sha256 } | ConvertTo-Json)
    return [pscustomobject]@{ jar=$jar; eula=(Join-Path $directory "eula.txt"); source="PaperMC Downloads Service"; build="Paper $ExpectedMinecraft build $($build.id)"; sha256=$hash }
}

function Set-TestEula([string] $Path) {
    # User-authorized only for disposable server directories owned by this harness.
    Set-Content -LiteralPath $Path -Value "eula=true" -Encoding ascii
}

function Add-Check($Checks, [string] $Name, [string] $Status, [string] $Detail, [string] $Evidence = "") {
    $category = if ($Name -match "Actual player command|Client-originated GUI|Multi-viewer GUI|Online/offline profile") { "client-dependent" }
        elseif ($Name -match "duplicate archive|one plugin.yml|server API classes|required application class|Paper GUI provider|Adventure runtime packaging|SQLite driver and native") { "packaging" }
        elseif ($Name -match "^Cycle [12] (clean process shutdown|localhost ports released)$") { "harness" }
        else { "server-runtime" }
    $Checks.Add([pscustomobject]@{ name = $Name; status = $Status; category = $category; cycle = $script:activeCycle; detail = $Detail; evidence = $Evidence })
}

function Write-RunReport([string] $Directory, [string] $Name, $Environment, $Checks) {
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $passed = @($Checks | Where-Object status -eq "PASS").Count
    $failed = @($Checks | Where-Object status -eq "FAIL").Count
    $notVerified = @($Checks | Where-Object status -eq "NOT VERIFIED").Count
    $categoryTotals = [ordered]@{}
    foreach ($category in @("packaging", "server-runtime", "harness", "client-dependent")) {
        $categoryChecks = @($Checks | Where-Object category -eq $category)
        $categoryTotals[$category] = [pscustomobject]@{
            total = $categoryChecks.Count
            passed = @($categoryChecks | Where-Object status -eq "PASS").Count
            failed = @($categoryChecks | Where-Object status -eq "FAIL").Count
            notVerified = @($categoryChecks | Where-Object status -eq "NOT VERIFIED").Count
        }
    }
    $result = [pscustomobject]@{
        schemaVersion = 1
        platform = $Name
        expectedMinecraftVersion = $ExpectedMinecraft
        gitHead = (& git -C $repoRoot rev-parse HEAD).Trim()
        workingTree = if ((& git -C $repoRoot status --porcelain)) { "modified" } else { "clean" }
        environment = $Environment
        startedUtc = $runId
        finishedUtc = [DateTime]::UtcNow.ToString("o")
        totals = [pscustomobject]@{ passed = $passed; failed = $failed; notVerified = $notVerified; byCategory = $categoryTotals }
        checks = @($Checks)
    }
    $jsonPath = Join-Path $Directory "results.json"
    $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8
    $lines = @("# $Name runtime acceptance", "", "- Expected Minecraft: $ExpectedMinecraft", "- Git HEAD: $($result.gitHead)",
        "- Started UTC: $runId", "- Passed: $passed", "- Failed: $failed", "- Not verified: $notVerified", "", "| Check | Status | Detail |", "|---|---|---|")
    foreach ($check in $Checks) {
        $detail = ($check.detail -replace '\|', '\\|' -replace "`r?`n", ' ')
        $lines += "| $($check.name) | $($check.status) | $detail |"
    }
    $lines += @("", "GUI client interactions require a real, authorized Minecraft client; no client-side success is inferred from server-side probes.")
    $markdownPath = Join-Path $Directory "report.md"
    Set-Content -LiteralPath $markdownPath -Value $lines -Encoding utf8
    $hashTargets = @($jsonPath, $markdownPath, (Join-Path $Directory "environment.json"),
        (Join-Path $Directory "server.log")) + @(Get-ChildItem -LiteralPath $Directory -Filter "cycle-*" -File -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    $hashes = @($hashTargets | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | ForEach-Object {
        $relative = $_.Substring($Directory.Length).TrimStart([char[]]@('\', '/'))
        [pscustomobject]@{ path = $relative; sha256 = Get-Sha256 $_ }
    })
    [pscustomobject]@{ algorithm = "SHA-256"; files = $hashes } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Directory "checksums.json") -Encoding utf8
    if ($failed -gt 0 -or $notVerified -gt 0) { $script:overallIncomplete = $true }
    Write-Host "$Name runtime report: $jsonPath (PASS=$passed FAIL=$failed NOT_VERIFIED=$notVerified)"
}

function Invoke-Gradle([string] $WorkingDirectory, [string[]] $Arguments) {
    Push-Location $WorkingDirectory
    try {
        & $gradle --no-daemon @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE): $($Arguments -join ' ')" }
    }
    finally { Pop-Location }
}

function Build-PlatformArtifacts([string] $Name) {
    Invoke-Gradle $repoRoot @("publishToMavenLocal")
    Invoke-Gradle (Join-Path $repoRoot "examples/bukkit-integration-example") @("shadowJar", "-PmdkPlatform=$Name")
    Invoke-Gradle (Join-Path $repoRoot "examples/bukkit-storage-example") @("test", "shadowJar", "-PmdkPlatform=$Name")
    Invoke-Gradle (Join-Path $repoRoot "integration-tests/runtime/probe") @("test", "shadowJar", "-PmdkPlatform=$Name")
}

function Test-PluginArtifact([string] $Name, [string] $Path, [string] $Platform, [string] $RequiredClass, [bool] $PaperExtensionExpected, [bool] $StoragePlugin, $Checks) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($Path)
        try {
            $entries = @($archive.Entries)
            $paths = @($entries | ForEach-Object FullName)
            $duplicates = @($paths | Group-Object | Where-Object Count -gt 1)
            Add-Check $Checks "$Name has no duplicate archive paths" $(if ($duplicates.Count -eq 0) { "PASS" } else { "FAIL" }) "duplicatePathCount=$($duplicates.Count)" $Path
            $pluginDescriptors = @($paths | Where-Object { $_ -eq "plugin.yml" }).Count
            Add-Check $Checks "$Name contains one plugin.yml" $(if ($pluginDescriptors -eq 1) { "PASS" } else { "FAIL" }) "pluginYmlCount=$pluginDescriptors" $Path
            $serverClasses = @($paths | Where-Object { $_ -match '^(org/bukkit|org/spigotmc|io/papermc/paper)/' }).Count
            Add-Check $Checks "$Name excludes server API classes" $(if ($serverClasses -eq 0) { "PASS" } else { "FAIL" }) "serverApiClassCount=$serverClasses" $Path
            $hasRequiredClass = $paths -contains $RequiredClass
            Add-Check $Checks "$Name contains required application class" $(if ($hasRequiredClass) { "PASS" } else { "FAIL" }) $RequiredClass $Path

            $paperClass = "com/trickshotmlg/devkit/paper/gui/PaperGuiPlatformExtension.class"
            $paperClassPresent = $paths -contains $paperClass
            $providerPath = "META-INF/services/com.trickshotmlg.devkit.bukkit.gui.BukkitGuiPlatformExtension"
            $providerEntries = @($archive.Entries | Where-Object FullName -eq $providerPath)
            $providerText = ""
            if ($providerEntries.Count -eq 1) {
                $reader = [IO.StreamReader]::new($providerEntries[0].Open())
                try { $providerText = $reader.ReadToEnd().Trim() } finally { $reader.Dispose() }
            }
            $expectedProviderCount = if ($PaperExtensionExpected) { 1 } else { 0 }
            $providerValid = if ($PaperExtensionExpected) { $providerEntries.Count -eq 1 -and $providerText -eq "com.trickshotmlg.devkit.paper.gui.PaperGuiPlatformExtension" -and $paperClassPresent } else { $providerEntries.Count -eq 0 -and -not $paperClassPresent }
            Add-Check $Checks "$Name isolates the optional Paper GUI provider" $(if ($providerValid) { "PASS" } else { "FAIL" }) "expected=$PaperExtensionExpected; serviceFiles=$($providerEntries.Count); provider=$providerText; extensionClass=$paperClassPresent" $Path

            $adventureApi = $paths -contains "net/kyori/adventure/text/Component.class"
            $adventureValid = if ($Platform -eq "spigot") { $adventureApi } else { -not $adventureApi }
            Add-Check $Checks "$Name uses the expected Adventure runtime packaging" $(if ($adventureValid) { "PASS" } else { "FAIL" }) "platform=$Platform; bundledAdventureApi=$adventureApi" $Path

            if ($StoragePlugin) {
                $sqliteDriver = $paths -contains "org/sqlite/JDBC.class"
                $sqliteNative = @($paths | Where-Object { $_ -like "org/sqlite/native/*" }).Count -gt 0
                Add-Check $Checks "$Name packages SQLite driver and native resources" $(if ($sqliteDriver -and $sqliteNative) { "PASS" } else { "FAIL" }) "driver=$sqliteDriver; nativeResource=$sqliteNative" $Path
            }
        }
        finally { $archive.Dispose() }
    }
    catch { Add-Check $Checks "$Name artifact inspection" "FAIL" $_.Exception.Message $Path }
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Read-Exact([System.IO.Stream] $Stream, [int] $Count) {
    $buffer = [byte[]]::new($Count)
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -le 0) { throw "RCON connection closed while reading a packet." }
        $offset += $read
    }
    return ,$buffer
}

function Send-RconPacket([System.IO.Stream] $Stream, [int] $Id, [int] $Type, [string] $Text) {
    $payload = [System.Text.Encoding]::ASCII.GetBytes($Text)
    $length = 4 + 4 + $payload.Length + 2
    $packet = [System.IO.MemoryStream]::new()
    $writer = [System.IO.BinaryWriter]::new($packet)
    $writer.Write([int]$length)
    $writer.Write([int]$Id)
    $writer.Write([int]$Type)
    $writer.Write($payload)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Flush()
    $bytes = $packet.ToArray()
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
    $writer.Dispose()
    $packet.Dispose()
}

function Receive-RconPacket([System.IO.Stream] $Stream) {
    $lengthBytes = Read-Exact $Stream 4
    $length = [BitConverter]::ToInt32($lengthBytes, 0)
    if ($length -lt 10 -or $length -gt 1048576) { throw "Invalid RCON packet length $length." }
    $payload = Read-Exact $Stream $length
    return [pscustomobject]@{
        id = [BitConverter]::ToInt32($payload, 0)
        type = [BitConverter]::ToInt32($payload, 4)
        text = [System.Text.Encoding]::ASCII.GetString($payload, 8, $length - 10)
    }
}

function Invoke-Rcon([int] $Port, [string] $Password, [string] $Command) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync([System.Net.IPAddress]::Loopback, $Port)
        if (-not $connect.Wait(3000)) { throw "Timed out connecting to local RCON." }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $stream.WriteTimeout = 5000
        Send-RconPacket $stream 1 3 $Password
        $auth = Receive-RconPacket $stream
        if ($auth.id -ne 1) { throw "Local RCON authentication failed (response id $($auth.id))." }
        Send-RconPacket $stream 2 2 $Command
        return (Receive-RconPacket $stream).text
    }
    finally { $client.Dispose() }
}

function Get-ServerLog([string] $ServerDirectory) {
    $parts = [System.Collections.Generic.List[string]]::new()
    $latest = Join-Path $ServerDirectory "logs/latest.log"
    if (Test-Path -LiteralPath $latest) { $parts.Add((Get-Content -LiteralPath $latest -Raw)) }
    foreach ($file in @("stdout.log", "stderr.log")) {
        $path = Join-Path $ServerDirectory $file
        if (Test-Path -LiteralPath $path) { $parts.Add((Get-Content -LiteralPath $path -Raw)) }
    }
    return ($parts -join "`n")
}

function Wait-ForServer([System.Diagnostics.Process] $Process, [string] $ServerDirectory, [int] $TimeoutSeconds) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if ($Process.HasExited) { throw "Server process exited early with code $($Process.ExitCode)." }
        if ((Get-ServerLog $ServerDirectory) -match 'Done \([^\r\n]+\)!') { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Server did not reach the ready log marker within $TimeoutSeconds seconds."
}

function Stop-Server([System.Diagnostics.Process] $Process, [int] $RconPort, [string] $Password, [int] $TimeoutSeconds) {
    if ($Process.HasExited) {
        return [pscustomobject]@{ clean = $false; detail = "Server process exited unexpectedly before shutdown (code $($Process.ExitCode))." }
    }
    try { $null = Invoke-Rcon $RconPort $Password "stop" } catch { }
    if ($Process.WaitForExit($TimeoutSeconds * 1000)) {
        return [pscustomobject]@{ clean = $true; detail = "Server accepted stop and exited normally." }
    }
    $processId = $Process.Id
    try {
        # This is the exact Process instance launched by this harness; never enumerate or kill unrelated Java processes.
        $Process.Kill()
        $Process.WaitForExit(10000) | Out-Null
    } catch { return [pscustomobject]@{ clean = $false; detail = "Server PID $processId exceeded shutdown timeout and owned-process cleanup failed: $($_.Exception.Message)" } }
    return [pscustomobject]@{ clean = $false; detail = "Server PID $processId required forced termination after $TimeoutSeconds seconds." }
}

function Reset-CycleMarkers([string] $ServerDirectory, [string] $PluginDirectory) {
    foreach ($stale in @((Join-Path $ServerDirectory "logs/latest.log"),
            (Join-Path $PluginDirectory "MdkRuntimeProbe/runtime-results.json"))) {
        if (Test-Path -LiteralPath $stale -PathType Leaf) { Remove-Item -LiteralPath $stale -Force }
    }
}

function Test-PortReleased([int] $Port) {
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        try { $listener.Start(); return $true }
        catch { Start-Sleep -Milliseconds 250 }
        finally { $listener.Stop() }
    }
    return $false
}

function Start-OneCycle([string] $Name, [int] $Cycle, [string] $ServerJar, [string] $ServerDirectory,
                        [string] $PluginDirectory, [int] $ServerPort, [int] $RconPort,
                        [string] $RconPassword, [string] $ResultsDirectory) {
    $stdoutPath = Join-Path $ServerDirectory "stdout.log"
    $stderrPath = Join-Path $ServerDirectory "stderr.log"
    $cycleChecks = [System.Collections.Generic.List[object]]::new()
    # Readiness markers must belong to this process, not the previous restart cycle.
    Reset-CycleMarkers $ServerDirectory $PluginDirectory
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaExecutable
    $startInfo.WorkingDirectory = $ServerDirectory
    $startInfo.Arguments = "-Xms512M -Xmx2G -Dmdk.runtime.platform=$Name -Dmdk.runtime.cycle=$Cycle -jar `"$ServerJar`" --nogui"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw "Could not start Java server process." }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    try {
        Wait-ForServer $process $ServerDirectory $StartupTimeoutSeconds
        Add-Check $cycleChecks "Server startup readiness" "PASS" "The launched server process emitted its current-cycle ready marker." "logs/latest.log"
        $log = Get-ServerLog $ServerDirectory
        Add-Check $cycleChecks "Server version" ($(if ($log -match '1\.21\.11') { "PASS" } else { "FAIL" })) "Startup log must identify Minecraft $ExpectedMinecraft." "logs/latest.log"
        Add-Check $cycleChecks "Example and probe plugins enabled" ($(if ($log -match 'MinecraftDevKit integration example enabled with services' -and $log -match 'Storage example ready; startup order=' -and $log -match '\[MDK-RUNTIME\] PASS dependent examples enabled') { "PASS" } else { "FAIL" })) "The two examples and test probe completed onEnable." "logs/latest.log"
        $linkage = $log -match 'ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError|ServiceConfigurationError'
        Add-Check $cycleChecks "No platform linkage errors" ($(if (-not $linkage) { "PASS" } else { "FAIL" })) "Scanned server and plugin startup logs for linkage/provider failures." "logs/latest.log"

        $probePath = Join-Path $PluginDirectory "MdkRuntimeProbe/runtime-results.json"
        $probeWait = [Diagnostics.Stopwatch]::StartNew()
        $requiredProbeChecks = @("sync task primary-thread ownership", "async-to-sync scheduler continuation", "repeating task lifecycle", "async storage to server-thread handoff")
        $probeReport = $null
        while ($probeWait.Elapsed.TotalSeconds -lt 45) {
            if (Test-Path -LiteralPath $probePath) {
                try { $probeReport = Get-Content -LiteralPath $probePath -Raw | ConvertFrom-Json } catch { $probeReport = $null }
                if ($probeReport -and @($probeReport.checks.name | Where-Object { $_ -in $requiredProbeChecks }).Count -eq $requiredProbeChecks.Count) { break }
            }
            Start-Sleep -Milliseconds 250
        }
        if ($probeReport) {
            foreach ($probeCheck in $probeReport.checks) {
                Add-Check $cycleChecks "Probe: $($probeCheck.name)" $probeCheck.status $probeCheck.detail "plugins/MdkRuntimeProbe/runtime-results.json"
            }
        } else {
            Add-Check $cycleChecks "Runtime probe completion" "FAIL" "Probe did not write its structured readiness checks before the timeout." "plugins/MdkRuntimeProbe/runtime-results.json"
        }

        foreach ($entry in @(
            @{ command = "mdkintegration status"; expected = "Lifecycle: RUNNING"; name = "Integration status command" },
            @{ command = "mdkintegration reload"; expected = "Reload completed"; name = "Integration reload command" },
            @{ command = "mdkstorage status"; expected = "Storage open=true, backend=sqlite, lifecycle=RUNNING"; name = "Storage status command" },
            @{ command = "mdkruntimeprobe nested 42"; expected = "MDK_RUNTIME_NESTED_42"; name = "Cloud nested typed argument" },
            @{ command = "mdkruntimeprobe status"; expected = "MDK_RUNTIME_PROBE"; name = "Probe command registration" }
        )) {
            try {
                $response = Invoke-Rcon $RconPort $RconPassword $entry.command
                $passed = $response.Contains($entry.expected)
                Add-Check $cycleChecks $entry.name ($(if ($passed) { "PASS" } else { "FAIL" })) $response "Local RCON command response"
            } catch { Add-Check $cycleChecks $entry.name "FAIL" $_.Exception.Message "Local RCON command" }
        }

        $dataChecks = @(
            @{ name = "Integration config generated"; path = Join-Path $PluginDirectory "MinecraftDevKitIntegrationExample/config.yml" },
            @{ name = "Translations initialized"; path = Join-Path $PluginDirectory "MinecraftDevKitIntegrationExample/lang/en.yml" },
            @{ name = "Storage config generated"; path = Join-Path $PluginDirectory "MinecraftDevKitStorageExample/config.yml" },
            @{ name = "Storage SQLite database created"; path = Join-Path $PluginDirectory "MinecraftDevKitStorageExample/players.db" },
            @{ name = "Probe persisted YAML exists"; path = Join-Path $PluginDirectory "MdkRuntimeProbe/probe.yml" },
            @{ name = "Probe SQLite database created"; path = Join-Path $PluginDirectory "MdkRuntimeProbe/runtime.sqlite" }
        )
        foreach ($dataCheck in $dataChecks) {
            Add-Check $cycleChecks $dataCheck.name ($(if (Test-Path -LiteralPath $dataCheck.path) { "PASS" } else { "FAIL" })) "Test-owned application data file presence." ($dataCheck.path.Substring($ServerDirectory.Length).TrimStart('\','/'))
        }

        return ,$cycleChecks
    }
    finally {
        $shutdown = $null
        try { $shutdown = Stop-Server $process $RconPort $RconPassword $ShutdownTimeoutSeconds }
        catch { $shutdown = [pscustomobject]@{ clean = $false; detail = "Shutdown handling failed: $($_.Exception.Message)" } }
        $script:lastShutdown = $shutdown
        try {
            if (-not $process.HasExited) { $process.WaitForExit(10000) | Out-Null }
            try { [IO.File]::WriteAllText($stdoutPath, $stdoutTask.Result) }
            catch { Add-Check $cycleChecks "stdout capture" "FAIL" $_.Exception.Message $stdoutPath }
            try { [IO.File]::WriteAllText($stderrPath, $stderrTask.Result) }
            catch { Add-Check $cycleChecks "stderr capture" "FAIL" $_.Exception.Message $stderrPath }
            $probePath = Join-Path $PluginDirectory "MdkRuntimeProbe/runtime-results.json"
            $reportCopy = Join-Path $ResultsDirectory "cycle-$Cycle-probe-results.json"
            $shutdownChecks = @("lifecycle shutdown and owned resource cleanup", "TaskScope shutdown", "Bukkit scheduler shutdown",
                "GUI manager shutdown", "SQLite storage shutdown", "async storage result completion")
            if (Test-Path -LiteralPath $probePath) {
                Copy-Item -LiteralPath $probePath -Destination $reportCopy -Force
                try {
                    $finalProbe = Get-Content -LiteralPath $probePath -Raw | ConvertFrom-Json
                    foreach ($probeCheck in $finalProbe.checks | Where-Object { $_.name -in $shutdownChecks }) {
                        Add-Check $cycleChecks "Probe: $($probeCheck.name)" $probeCheck.status $probeCheck.detail "cycle-$Cycle-probe-results.json"
                    }
                } catch { Add-Check $cycleChecks "Final probe report parsing" "FAIL" $_.Exception.Message "cycle-$Cycle-probe-results.json" }
            } else {
                Add-Check $cycleChecks "Final probe shutdown report" "FAIL" "Probe did not write its shutdown assertions during disable." "plugins/MdkRuntimeProbe/runtime-results.json"
            }
            $finalLog = Get-ServerLog $ServerDirectory
            $finalLinkage = $finalLog -match 'ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError|ServiceConfigurationError'
            Add-Check $cycleChecks "No linkage errors through shutdown" $(if (-not $finalLinkage) { "PASS" } else { "FAIL" }) "Scanned final server and plugin logs through disable." "logs/latest.log"
            foreach ($capture in @(
                @{ source = $stdoutPath; suffix = "stdout.log" },
                @{ source = $stderrPath; suffix = "stderr.log" },
                @{ source = (Join-Path $ServerDirectory "logs/latest.log"); suffix = "server.log" }
            )) {
                if (Test-Path -LiteralPath $capture.source -PathType Leaf) {
                    Copy-Item -LiteralPath $capture.source -Destination (Join-Path $ResultsDirectory "cycle-$Cycle-$($capture.suffix)") -Force
                }
            }
        } catch { Add-Check $cycleChecks "Harness evidence capture" "FAIL" $_.Exception.Message "cycle-$Cycle" }
        finally { $process.Dispose() }
    }
}

function Run-Platform([string] $Name) {
    $checks = [System.Collections.Generic.List[object]]::new()
    $serverJar = if ($Name -eq "spigot") { $SpigotServerJar } else { $PaperServerJar }
    $eulaFile = if ($Name -eq "spigot") { $SpigotEulaFile } else { $PaperEulaFile }
    $platformResults = Join-Path $baseResults "$Name/$runId"
    $environment = [ordered]@{ java = $JavaExecutable; javaVersion = $null; serverJarName = if ($serverJar) { [IO.Path]::GetFileName($serverJar) } else { $null }; serverJarSha256 = $null; integrationPluginSha256 = $null; storagePluginSha256 = $null; probePluginSha256 = $null; acceptedEulaFileProvided = [bool]$eulaFile; serverBuild = $null }
    $failureStatus = "FAIL"
    New-Item -ItemType Directory -Path $platformResults -Force | Out-Null

    try {
        if ($Provision) {
            $artifactInfo = if ($Name -eq "spigot") { Get-OrProvisionSpigot } else { Get-OrProvisionPaper }
            $serverJar = $artifactInfo.jar
            $eulaFile = $null
            $environment.serverBuild = $artifactInfo.build
            $environment.provisionSource = $artifactInfo.source
            $environment.serverJarSha256 = $artifactInfo.sha256
            $environment.serverJarName = [IO.Path]::GetFileName($artifactInfo.jar)
            $environment.eulaMode = "eula=true configured in this disposable harness-owned server directory under explicit user authorization"
        }
        if (-not $SkipBuild) { Build-PlatformArtifacts $Name }
        $integrationJar = Join-Path $repoRoot "examples/bukkit-integration-example/build/libs/minecraftdevkit-bukkit-integration-example-0.1.0-SNAPSHOT.jar"
        $storageJar = Join-Path $repoRoot "examples/bukkit-storage-example/build/libs/bukkit-storage-example-0.1.0-SNAPSHOT.jar"
        $probeJar = Join-Path $repoRoot "integration-tests/runtime/probe/build/libs/minecraftdevkit-runtime-probe-0.1.0-SNAPSHOT.jar"
        foreach ($artifact in @($integrationJar, $storageJar, $probeJar)) {
            if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Required plugin artifact is missing: $artifact" }
        }
        $environment.integrationPluginSha256 = Get-Sha256 $integrationJar
        $environment.storagePluginSha256 = Get-Sha256 $storageJar
        $environment.probePluginSha256 = Get-Sha256 $probeJar
        $paperExpected = $Name -eq "paper"
        Test-PluginArtifact "Integration example" $integrationJar $Name "com/trickshotmlg/devkit/examples/integration/IntegrationExamplePlugin.class" $paperExpected $false $checks
        Test-PluginArtifact "Storage example" $storageJar $Name "com/trickshotmlg/devkit/examples/storage/StorageExamplePlugin.class" $false $true $checks
        Test-PluginArtifact "Runtime probe" $probeJar $Name "com/trickshotmlg/devkit/runtimeprobe/RuntimeProbePlugin.class" $paperExpected $false $checks
        $failureStatus = "NOT VERIFIED"
        if ($Provision) { $environment.javaVersion = Assert-Java21 $JavaExecutable }
        elseif (-not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf) -and -not (Get-Command $JavaExecutable -ErrorAction SilentlyContinue)) { throw "Java executable not found: $JavaExecutable" }
        $versionInfo = [Diagnostics.ProcessStartInfo]::new()
        $versionInfo.FileName = $JavaExecutable
        $versionInfo.Arguments = "-version"
        $versionInfo.UseShellExecute = $false
        $versionInfo.RedirectStandardError = $true
        $versionInfo.RedirectStandardOutput = $true
        $versionProcess = [Diagnostics.Process]::Start($versionInfo)
        try {
            $versionText = $versionProcess.StandardError.ReadToEnd() + $versionProcess.StandardOutput.ReadToEnd()
            $versionProcess.WaitForExit()
            if ($versionProcess.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($versionText)) { throw "Could not read Java runtime version from $JavaExecutable" }
            $environment.javaVersion = ($versionText -split "`r?`n")[0]
        } finally { $versionProcess.Dispose() }
        Set-Content -LiteralPath (Join-Path $platformResults "environment.json") -Value ($environment | ConvertTo-Json -Depth 5) -Encoding utf8
        if ([string]::IsNullOrWhiteSpace($serverJar) -or -not (Test-Path -LiteralPath $serverJar -PathType Leaf)) {
            throw "A local $Name $ExpectedMinecraft server JAR is required. Set MDK_$($Name.ToUpperInvariant())_SERVER_JAR or pass the corresponding *ServerJar parameter; use -Provision to obtain official artifacts."
        }
        $serverJar = (Resolve-Path -LiteralPath $serverJar).Path
        $environment.serverJarSha256 = Get-Sha256 $serverJar
        Set-Content -LiteralPath (Join-Path $platformResults "environment.json") -Value ($environment | ConvertTo-Json -Depth 5) -Encoding utf8
        if (-not $Provision -and ([string]::IsNullOrWhiteSpace($eulaFile) -or -not (Test-Path -LiteralPath $eulaFile -PathType Leaf))) {
            throw "An accepted EULA file is required. Supply -$($Name)EulaFile, or use -Provision for this harness-owned disposable test server (EULA acceptance was explicitly authorized for these instances)."
        }
        if (-not $Provision) { $eulaFile = (Resolve-Path -LiteralPath $eulaFile).Path }
        if (-not $Provision -and -not (Get-Content -LiteralPath $eulaFile | Where-Object { $_.Trim() -match '^eula=true$' })) {
            throw "The supplied EULA file does not contain eula=true. No server was started or EULA modified."
        }
        $failureStatus = "FAIL"
        $runDirectory = Join-Path $platformResults "server"
        $pluginsDirectory = Join-Path $runDirectory "plugins"
        New-Item -ItemType Directory -Path $pluginsDirectory -Force | Out-Null
        if ($Provision) { Set-TestEula (Join-Path $runDirectory "eula.txt") }
        else { Copy-Item -LiteralPath $eulaFile -Destination (Join-Path $runDirectory "eula.txt") -Force }
        foreach ($artifact in @($integrationJar, $storageJar, $probeJar)) { Copy-Item -LiteralPath $artifact -Destination $pluginsDirectory -Force }
        $serverPort = Get-FreeLoopbackPort
        $rconPort = Get-FreeLoopbackPort
        while ($rconPort -eq $serverPort) { $rconPort = Get-FreeLoopbackPort }
        $password = [Guid]::NewGuid().ToString("N") + [Guid]::NewGuid().ToString("N")
        $properties = @(
            "server-ip=127.0.0.1", "server-port=$serverPort", "online-mode=true", "max-players=4",
            "spawn-protection=0", "view-distance=3", "simulation-distance=3", "level-name=world",
            "enable-rcon=true", "rcon.ip=127.0.0.1", "rcon.port=$rconPort", "rcon.password=$password",
            "enable-query=false", "enable-status=true", "motd=MinecraftDevKit runtime acceptance"
        )
        Set-Content -LiteralPath (Join-Path $runDirectory "server.properties") -Value $properties -Encoding ascii
        $environment.serverPort = $serverPort
        $environment.rconBoundTo = "127.0.0.1"
        Set-Content -LiteralPath (Join-Path $platformResults "environment.json") -Value ($environment | ConvertTo-Json -Depth 5) -Encoding utf8

        for ($cycle = 1; $cycle -le 2; $cycle++) {
            $script:activeCycle = $cycle
            $cycleChecks = Start-OneCycle $Name $cycle $serverJar $runDirectory $pluginsDirectory $serverPort $rconPort $password $platformResults
            foreach ($check in $cycleChecks) { $checks.Add($check) }
            Add-Check $checks "Cycle $cycle clean process shutdown" ($(if ($script:lastShutdown.clean) { "PASS" } else { "FAIL" })) $script:lastShutdown.detail "stdout.log, stderr.log, logs/latest.log"
            $portsFree = (Test-PortReleased $serverPort) -and (Test-PortReleased $rconPort)
            Add-Check $checks "Cycle $cycle localhost ports released" ($(if ($portsFree) { "PASS" } else { "FAIL" })) "Server and local RCON ports are bindable after process exit." "server-port=$serverPort;rcon-port=$rconPort"
            if (-not $script:lastShutdown.clean) { break }
        }

        Add-Check $checks "Actual player command and permission behavior" "NOT VERIFIED" "No authenticated player client is configured; console execution does not establish player-sender behavior." "Manual steps in README.md"
        Add-Check $checks "Client-originated GUI click/drag/navigation" "NOT VERIFIED" "A real client is required; no simulated event is counted as client acceptance." "Manual steps in README.md"
        Add-Check $checks "Multi-viewer GUI session isolation" "NOT VERIFIED" "Requires two real authorized players." "Manual steps in README.md"
        Add-Check $checks "Online/offline profile completion" "NOT VERIFIED" "No real player profile service/client interaction was exercised." "Manual steps in README.md"
        Add-Check $checks "Version/build identity" "NOT VERIFIED" "Server startup log records the Minecraft version; exact upstream build identity is retained in logs but not cryptographically attested by this harness." "logs/latest.log"
    }
    catch {
        $checkName = if ($failureStatus -eq "NOT VERIFIED") { "Runtime prerequisite" } else { "Runtime execution" }
        Add-Check $checks $checkName $failureStatus $_.Exception.Message ""
        if (Test-Path -LiteralPath (Join-Path $platformResults "server/logs/latest.log")) {
            Copy-Item -LiteralPath (Join-Path $platformResults "server/logs/latest.log") -Destination (Join-Path $platformResults "server.log") -Force
        }
    }
    finally {
        $script:activeCycle = $null
        if (Test-Path -LiteralPath (Join-Path $platformResults "server/logs/latest.log")) {
            Copy-Item -LiteralPath (Join-Path $platformResults "server/logs/latest.log") -Destination (Join-Path $platformResults "server.log") -Force
        }
        Write-RunReport $platformResults $Name $environment $checks
    }
}

if ($env:MDK_RUNTIME_HARNESS_TEST_MODE -eq "1") { return }

foreach ($name in $platforms) { Run-Platform $name }

function Get-MatrixStatus($Checks, [string] $Pattern) {
    $matches = @($Checks | Where-Object { $_.name -match $Pattern })
    if ($matches.Count -eq 0) { return "NOT VERIFIED" }
    if (@($matches | Where-Object status -eq "FAIL").Count -gt 0) { return "FAIL" }
    if (@($matches | Where-Object status -eq "NOT VERIFIED").Count -gt 0) { return "NOT VERIFIED" }
    if (@($matches | Where-Object status -eq "PASS").Count -eq $matches.Count) { return "PASS" }
    return "NOT VERIFIED"
}

$matrixRows = @(
    @{ label="Server startup and restart cycles"; pattern="Server startup readiness" },
    @{ label="Plugin loading and common classloading"; pattern="Example and probe plugins enabled|common adapter classloading" },
    @{ label="Commands and nested command probes"; pattern="Integration status command|Cloud nested typed argument|Probe command registration" },
    @{ label="Lifecycle startup and cleanup"; pattern="Bukkit lifecycle start|lifecycle shutdown and owned resource cleanup" },
    @{ label="Scheduler and async handoff"; pattern="sync task primary-thread ownership|async-to-sync scheduler continuation|async storage to server-thread handoff" },
    @{ label="Flyway discovery and SQLite persistence"; pattern="SQLite JDBC and restart-persistent Flyway migration|SQLite storage database created" },
    @{ label="Clean shutdown and port release"; pattern="Cycle [12] clean process shutdown|Cycle [12] localhost ports released" },
    @{ label="Packaged artifact integrity"; pattern="duplicate archive paths|one plugin.yml|excludes server API classes|required application class" },
    @{ label="Platform GUI provider and Adventure packaging"; pattern="isolates the optional Paper GUI provider|expected Adventure runtime packaging" },
    @{ label="Real-player GUI interactions"; pattern="Client-originated GUI click/drag/navigation" },
    @{ label="Player command permissions"; pattern="Actual player command and permission behavior" }
)
$matrixLines = @("# Runtime compatibility matrix", "", "Run: $runId", "", "| Capability | Spigot 1.21.11 | Paper 1.21.11 |", "|---|---|---|")
$platformReports = @{}
foreach ($name in $platforms) {
    $reportPath = Join-Path $baseResults "$name/$runId/results.json"
    if (Test-Path -LiteralPath $reportPath) { $platformReports[$name] = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json }
}
foreach ($row in $matrixRows) {
    $cells = foreach ($name in @("spigot", "paper")) {
        if ($name -notin $platforms -or -not $platformReports.ContainsKey($name)) { "NOT VERIFIED" }
        else { Get-MatrixStatus $platformReports[$name].checks $row.pattern }
    }
    $matrixLines += "| $($row.label) | $($cells[0]) | $($cells[1]) |"
}
$matrixPath = Join-Path $baseResults "$runId-compatibility-matrix.md"
Set-Content -LiteralPath $matrixPath -Value $matrixLines -Encoding utf8

$inventoryTargets = @($matrixPath)
foreach ($name in $platforms) {
    $dir = Join-Path $baseResults "$name/$runId"
    $inventoryTargets += @(Get-ChildItem -LiteralPath $dir -File -Recurse | Select-Object -ExpandProperty FullName)
}
$inventoryFiles = @($inventoryTargets | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Sort-Object -Unique | ForEach-Object {
    $relative = $_.Substring($repoRoot.Length).TrimStart([char[]]@('\','/'))
    [pscustomobject]@{ path=$relative; sha256=(Get-Sha256 $_) }
})
[pscustomobject]@{ algorithm="SHA-256"; files=$inventoryFiles } | ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath (Join-Path $baseResults "$runId-checksums.json") -Encoding utf8

$summary = [pscustomobject]@{
    schemaVersion = 1
    runId = $runId
    platforms = @($platforms)
    reportDirectories = @($platforms | ForEach-Object { Join-Path $baseResults "$_/$runId" })
    compatibilityMatrix = $matrixPath
    checksumInventory = Join-Path $baseResults "$runId-checksums.json"
    acceptance = if ($overallIncomplete) { "INCOMPLETE" } else { "COMPLETE" }
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $baseResults "$runId-summary.json") -Encoding utf8
if ($overallIncomplete) { exit 2 }
exit 0
