$ErrorActionPreference = 'Stop'
$env:MDK_RUNTIME_HARNESS_TEST_MODE = '1'
. (Join-Path $PSScriptRoot '../run.ps1')
Remove-Item Env:\MDK_RUNTIME_HARNESS_TEST_MODE -ErrorAction SilentlyContinue

$script:assertions = 0
function Assert([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    $script:assertions++
    Write-Host "PASS: $Message"
}

$temp = Join-Path ([IO.Path]::GetTempPath()) ("mdk-harness-tests-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    $reportDir = Join-Path $temp 'report'
    $checks = [Collections.Generic.List[object]]::new()
    Add-Check $checks 'missing server artifact' 'NOT VERIFIED' 'Explicit prerequisite missing.'
    Write-RunReport $reportDir 'spigot' ([ordered]@{ java='java'; serverJarSha256=$null }) $checks
    $report = Get-Content (Join-Path $reportDir 'results.json') -Raw | ConvertFrom-Json
    Assert ($report.totals.notVerified -eq 1 -and $report.checks[0].status -eq 'NOT VERIFIED') 'structured prerequisite report records NOT VERIFIED'

    $payload = Join-Path $temp 'payload.bin'; [IO.File]::WriteAllBytes($payload, [byte[]](1..255) + [byte[]](1..255) + [byte[]](1..255) + [byte[]](1..255) + [byte[]](1..255))
    # Keep provisioning tests hermetic: use tiny local fixture bytes and point the existing
    # cache lookup functions at a temporary directory. These tests must never invoke BuildTools
    # or the Paper downloads API.
    $fixtureProvision = Join-Path $temp 'provision fixtures'
    $fixtureSpigotDir = Join-Path $fixtureProvision 'spigot'
    $fixturePaperDir = Join-Path $fixtureProvision 'paper'
    New-Item -ItemType Directory -Path $fixtureSpigotDir, $fixturePaperDir -Force | Out-Null
    Copy-Item -LiteralPath $payload -Destination (Join-Path $fixtureSpigotDir 'spigot-1.21.11.jar')
    Copy-Item -LiteralPath $payload -Destination (Join-Path $fixturePaperDir 'paper.jar')
    $fixtureHash = (Get-FileHash $payload -Algorithm SHA256).Hash.ToLowerInvariant()
    $provisionRoot = $fixtureProvision
    $knownSpigotSha256 = $fixtureHash
    $knownPaperSha256 = $fixtureHash
    $spigot = Get-OrProvisionSpigot
    $paper = Get-OrProvisionPaper
    Assert ((Get-Sha256 $spigot.jar) -eq $knownSpigotSha256 -and $spigot.source -match 'cached') 'valid cached Spigot fixture reused without invoking BuildTools'
    Assert ((Get-Sha256 $paper.jar) -eq $knownPaperSha256 -and $paper.source -match 'cached') 'valid cached Paper fixture reused without network access'
    Assert (Test-ArtifactHash $paper.jar $knownPaperSha256) 'cache checksum predicate accepts matching fixture bytes'
    Assert (-not (Test-ArtifactHash $paper.jar ('0' * 64))) 'corrupted or wrong-version cache hash is rejected'

    function Invoke-WebRequest { param($Uri, $OutFile, [switch] $UseBasicParsing, $TimeoutSec); Copy-Item -LiteralPath $script:testPayload -Destination $OutFile -Force }
    $script:testPayload = $payload
    $expected = (Get-FileHash $payload -Algorithm SHA256).Hash.ToLowerInvariant()
    $download = Join-Path $temp 'valid download.bin'
    Assert ((Invoke-VerifiedDownload 'https://unit.test/artifact' $download $expected) -eq $expected) 'download checksum validates and supports spaces in destination path'
    $bad = $false
    try { Invoke-VerifiedDownload 'https://unit.test/artifact' (Join-Path $temp 'bad.bin') ('0' * 64) | Out-Null } catch { $bad = $true }
    Assert ($bad -and -not (Test-Path (Join-Path $temp 'bad.bin.download'))) 'checksum mismatch fails and removes partial download'
    Remove-Item Function:\Invoke-WebRequest

    $serverDir = Join-Path $temp 'server'; $pluginDir = Join-Path $serverDir 'plugins'
    New-Item -ItemType Directory -Force (Join-Path $serverDir 'logs'), (Join-Path $pluginDir 'MdkRuntimeProbe') | Out-Null
    $ownedEula = Join-Path $serverDir 'eula.txt'; Set-TestEula $ownedEula
    Assert ((Get-Content $ownedEula -Raw).Trim() -eq 'eula=true') 'authorized EULA setting is confined to disposable test directory'
    $latest = Join-Path $serverDir 'logs/latest.log'; $probe = Join-Path $pluginDir 'MdkRuntimeProbe/runtime-results.json'
    $interactiveMarker = Join-Path $pluginDir 'MdkRuntimeProbe/interactive-results.json'
    Set-Content $latest 'Done (old cycle)!'; Set-Content $probe '{"cycle":1}'; Set-Content $interactiveMarker '{"complete":true,"cycle":1}'
    Reset-CycleMarkers $serverDir $pluginDir
    Assert (-not (Test-Path $latest) -and -not (Test-Path $probe) -and -not (Test-Path $interactiveMarker)) 'cycle reset removes stale readiness, probe, and interactive markers'

    $interactiveReportDir = Join-Path $temp 'interactive-report'
    $interactiveChecks = [Collections.Generic.List[object]]::new()
    Add-Check $interactiveChecks 'Interactive session completion' 'NOT VERIFIED' 'No real client session was executed.'
    Write-RunReport $interactiveReportDir 'paper' ([ordered]@{ interactiveMode=$true }) $interactiveChecks
    $interactiveReport = Get-Content (Join-Path $interactiveReportDir 'results.json') -Raw | ConvertFrom-Json
    Assert ($interactiveReport.totals.byCategory.'client-dependent'.notVerified -eq 1) 'interactive no-client result stays NOT VERIFIED in its own report category'
    $completeSession = [pscustomobject]@{ status='PASS'; complete=$true; sessionId='current-session'; platform='paper'; progress=[pscustomobject]@{completed=2;total=2;failed=0;remaining=0}; requirements=@([pscustomobject]@{status='PASS'},[pscustomobject]@{status='PASS'}) }
    Assert (Test-InteractiveResultPassed $completeSession 'paper') 'harness accepts a matching complete session with every requirement passed'
    $incompleteSession = $completeSession | Select-Object *
    $incompleteSession.requirements = @([pscustomobject]@{status='PASS'},[pscustomobject]@{status='PENDING'})
    Assert (-not (Test-InteractiveResultPassed $incompleteSession 'paper')) 'harness rejects a completion flag while any required scenario remains pending'
    $inconsistentSession = $completeSession | Select-Object *
    $inconsistentSession.progress = [pscustomobject]@{completed=1;total=2;failed=0;remaining=1}
    Assert (-not (Test-InteractiveResultPassed $inconsistentSession 'paper')) 'harness rejects inconsistent completion counts'
    Assert (-not (Test-InteractiveResultPassed $completeSession 'spigot')) 'harness rejects a terminal result from another platform'

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $nativeJar = Join-Path $temp 'native-example.jar'
    $nativeArchive = [IO.Compression.ZipFile]::Open($nativeJar, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $descriptor = $nativeArchive.CreateEntry('paper-plugin.yml')
        $writer = [IO.StreamWriter]::new($descriptor.Open())
        try { $writer.Write("name: MinecraftDevKitIntegrationExample`nmain: com.trickshotmlg.devkit.examples.integration.IntegrationExamplePlugin`nbootstrapper: com.trickshotmlg.devkit.examples.integration.IntegrationExampleBootstrap`n") } finally { $writer.Dispose() }
        $mainClass = $nativeArchive.CreateEntry('com/trickshotmlg/devkit/examples/integration/IntegrationExamplePlugin.class')
        $mainClass.Open().Dispose()
    } finally { $nativeArchive.Dispose() }
    $nativeChecks = [Collections.Generic.List[object]]::new()
    Test-PluginArtifact 'Integration example' $nativeJar 'paper' 'com/trickshotmlg/devkit/examples/integration/IntegrationExamplePlugin.class' $false $false $nativeChecks $true
    $descriptorCheck = @($nativeChecks | Where-Object name -eq 'Integration example contains the native Paper descriptor only')
    Assert ($descriptorCheck.Count -eq 1 -and $descriptorCheck[0].status -eq 'PASS') 'native Paper artifact check requires paper-plugin.yml, main, bootstrapper, and excludes plugin.yml'

    $java = Join-Path $PSHOME 'powershell.exe'
    $exitProcess = Start-Process -FilePath $java -ArgumentList '-NoProfile -Command exit 17' -PassThru -WindowStyle Hidden
    $exitProcess.WaitForExit(10000) | Out-Null
    $waitFailed = $false
    try { Wait-ForServer $exitProcess $serverDir 2 } catch { $waitFailed = $_.Exception.Message -match 'exited early' }
    Assert $waitFailed 'unexpected child exit is classified as startup failure'
    $unexpectedShutdown = Stop-Server $exitProcess 1 'unused' 1
    Assert (-not $unexpectedShutdown.clean) 'already-exited server is not mislabeled as graceful shutdown'
    $exitProcess.Dispose()

    $missingExecutable = [Diagnostics.ProcessStartInfo]::new()
    $missingExecutable.FileName = Join-Path $temp 'missing executable.exe'
    $startFailed = $false
    try { [Diagnostics.Process]::Start($missingExecutable) | Out-Null } catch { $startFailed = $true }
    Assert $startFailed 'process startup failure is observable instead of being treated as a server run'

    $sleepProcess = Start-Process -FilePath $java -ArgumentList '-NoProfile -Command Start-Sleep -Seconds 30' -PassThru -WindowStyle Hidden
    try {
        $timedOut = $false
        try { Wait-ForServer $sleepProcess $serverDir 1 } catch { $timedOut = $_.Exception.Message -match 'ready log marker' }
        Assert $timedOut 'server readiness timeout is bounded and reported'
        function Invoke-Rcon { param($Port, $Password, $Command) }
        $stopped = Stop-Server $sleepProcess 1 'unused' 1
        Assert (-not $stopped.clean -and $sleepProcess.HasExited) 'shutdown timeout force-cleans only the owned process and records failure'
    } finally {
        if (-not $sleepProcess.HasExited) { $sleepProcess.Kill(); $sleepProcess.WaitForExit(5000) | Out-Null }
        $sleepProcess.Dispose()
        Remove-Item Function:\Invoke-Rcon -ErrorAction SilentlyContinue
    }

    Write-Host "Runtime harness regression checks passed: $script:assertions"
} finally {
    if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
