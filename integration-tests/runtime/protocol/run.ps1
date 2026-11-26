param(
    [ValidateSet('paper', 'spigot')]
    [string] $Platform = 'paper',
    [string] $ServerJar,
    [string] $JavaExecutable = 'java',
    [ValidateRange(60, 900)] [int] $ClientTimeoutSeconds = 240,
    [ValidateSet('legacy', 'native')] [string] $CommandBackend = 'legacy',
    [string] $Profiles = 'none,operator,inspect,modify,region,region-admin,admin,all,broad-deny',
    [switch] $PermissionAwareCompletions
)

# This is an opt-in packet fixture. It is deliberately separate from run.ps1's authenticated,
# online-mode acceptance servers and is always loopback-only with online-mode=false.
$ErrorActionPreference = 'Stop'
$profileList = @($Profiles -split ',' | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ })
$supportedProfiles = @('none', 'operator', 'inspect', 'modify', 'region', 'region-admin', 'admin', 'all', 'broad-deny')
if (-not $profileList.Count -or @($profileList | Where-Object { $_ -notin $supportedProfiles }).Count) { throw "Unsupported protocol profile list: $Profiles" }
if ($Platform -eq 'spigot' -and $CommandBackend -ne 'legacy') { throw 'The native Paper backend is only available on Paper.' }
if ($Platform -eq 'spigot' -and $PermissionAwareCompletions) { throw 'The Paper completion filter cannot be enabled on Spigot.' }
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$gradle = Join-Path $repoRoot 'gradlew.bat'
$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$acceptanceRoot = Join-Path $repoRoot 'build/runtime-acceptance'
$known = if ($Platform -eq 'paper') {
    @{ path = (Join-Path $acceptanceRoot 'provision/paper/paper.jar'); sha256 = '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba' }
} else {
    @{ path = (Join-Path $acceptanceRoot 'provision/spigot/spigot-1.21.11.jar'); sha256 = 'da8b3f6e150d2da80e8a25a230c0344494a36001aa6ed2e8e527dfae3c58db5f' }
}
if (-not $ServerJar) { $ServerJar = $known.path }
if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) { throw "Server jar is missing: $ServerJar. Provision it first with integration-tests/runtime/run.ps1 -Provision." }
$actualServerHash = (Get-FileHash -LiteralPath $ServerJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualServerHash -ne $known.sha256) { throw "Refusing unverified $Platform 1.21.11 artifact. Expected $($known.sha256), got $actualServerHash." }
$java = Get-Command $JavaExecutable -ErrorAction SilentlyContinue
if (-not $java -and -not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf)) { throw "Java executable not found: $JavaExecutable" }
$javaPath = if ($java) { $java.Source } else { (Resolve-Path -LiteralPath $JavaExecutable).Path }
$javaVersionProcess = [Diagnostics.ProcessStartInfo]::new(); $javaVersionProcess.FileName = $javaPath; $javaVersionProcess.Arguments = '-version'
$javaVersionProcess.UseShellExecute = $false; $javaVersionProcess.RedirectStandardError = $true; $javaVersionProcess.RedirectStandardOutput = $true
$javaVersion = [Diagnostics.Process]::Start($javaVersionProcess)
$javaInfo = $javaVersion.StandardError.ReadToEnd() + $javaVersion.StandardOutput.ReadToEnd(); $javaVersion.WaitForExit()
if ($javaVersion.ExitCode -ne 0 -or $javaInfo -notmatch 'version "21(?:\.|"|\+)') { throw "Java 21 is required for this protocol fixture. Found: $javaInfo" }

function Invoke-Gradle([string] $WorkingDirectory, [string[]] $Arguments) {
    Push-Location $WorkingDirectory
    try { & $gradle --no-daemon @Arguments; if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE): $($Arguments -join ' ')" } }
    finally { Pop-Location }
}

function Get-FreePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Read-Exact([IO.Stream] $Stream, [int] $Count) {
    $buffer = [byte[]]::new($Count); $offset = 0
    while ($offset -lt $Count) { $read = $Stream.Read($buffer, $offset, $Count - $offset); if ($read -le 0) { throw 'RCON socket closed.' }; $offset += $read }
    return ,$buffer
}

function Send-Rcon([IO.Stream] $Stream, [int] $Id, [int] $Type, [string] $Text) {
    $payload = [Text.Encoding]::ASCII.GetBytes($Text); $length = 4 + 4 + $payload.Length + 2
    $packet = [IO.MemoryStream]::new(); $writer = [IO.BinaryWriter]::new($packet)
    $writer.Write([int]$length); $writer.Write([int]$Id); $writer.Write([int]$Type); $writer.Write($payload); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Flush()
    $bytes = $packet.ToArray(); $Stream.Write($bytes, 0, $bytes.Length); $Stream.Flush(); $writer.Dispose(); $packet.Dispose()
}

function Receive-Rcon([IO.Stream] $Stream) {
    $length = [BitConverter]::ToInt32((Read-Exact $Stream 4), 0)
    if ($length -lt 10 -or $length -gt 1048576) { throw "Invalid RCON packet size: $length" }
    $payload = Read-Exact $Stream $length
    return [pscustomobject]@{ id = [BitConverter]::ToInt32($payload, 0); type = [BitConverter]::ToInt32($payload, 4); text = [Text.Encoding]::ASCII.GetString($payload, 8, $length - 10) }
}

function Invoke-Rcon([int] $Port, [string] $Password, [string] $Text) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync([Net.IPAddress]::Loopback, $Port)
        if (-not $connect.Wait(3000)) { throw 'RCON connect timeout.' }
        $stream = $client.GetStream(); $stream.ReadTimeout = 5000; $stream.WriteTimeout = 5000
        Send-Rcon $stream 11 3 $Password; $auth = Receive-Rcon $stream
        if ($auth.id -ne 11) { throw "RCON auth failed ($($auth.id))." }
        Send-Rcon $stream 12 2 $Text
        return (Receive-Rcon $stream).text
    } finally { $client.Dispose() }
}

function Test-PortReleased([int] $Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start(); return $true } catch { return $false } finally { $listener.Stop() }
}

# Reuse the same three plugin artifacts as the normal runtime harness.
Invoke-Gradle $repoRoot @('publishToMavenLocal')
$platformMode = $Platform
Invoke-Gradle (Join-Path $repoRoot 'examples/bukkit-integration-example') @('shadowJar', "-PmdkPlatform=$platformMode")
Invoke-Gradle (Join-Path $repoRoot 'examples/bukkit-storage-example') @('shadowJar', "-PmdkPlatform=$Platform")
Invoke-Gradle (Join-Path $repoRoot 'integration-tests/runtime/probe') @('test', 'shadowJar', "-PmdkPlatform=$Platform")

$serverDirectory = Join-Path $acceptanceRoot "protocol/$Platform-1.21.11/$runId"
$pluginDirectory = Join-Path $serverDirectory 'plugins'
$resultsDirectory = Join-Path $serverDirectory 'results'
$clientDirectory = Join-Path $acceptanceRoot 'protocol/client-dependencies'
New-Item -ItemType Directory -Force -Path $pluginDirectory, $resultsDirectory, $clientDirectory | Out-Null
Copy-Item -LiteralPath $ServerJar -Destination (Join-Path $serverDirectory 'server.jar')
Copy-Item -LiteralPath (Join-Path $repoRoot 'examples/bukkit-integration-example/build/libs/minecraftdevkit-bukkit-integration-example-0.1.0-SNAPSHOT.jar') -Destination (Join-Path $pluginDirectory 'IntegrationExample.jar')
Copy-Item -LiteralPath (Join-Path $repoRoot 'examples/bukkit-storage-example/build/libs/bukkit-storage-example-0.1.0-SNAPSHOT.jar') -Destination (Join-Path $pluginDirectory 'StorageExample.jar')
Copy-Item -LiteralPath (Join-Path $repoRoot 'integration-tests/runtime/probe/build/libs/minecraftdevkit-runtime-probe-0.1.0-SNAPSHOT.jar') -Destination (Join-Path $pluginDirectory 'MdkRuntimeProbe.jar')
Set-Content -LiteralPath (Join-Path $serverDirectory 'eula.txt') -Value 'eula=true' -Encoding ascii
$gamePort = Get-FreePort; $rconPort = Get-FreePort
$rconPassword = [Guid]::NewGuid().ToString('N')
@("server-ip=127.0.0.1", "server-port=$gamePort", 'online-mode=false', 'enable-rcon=true', "rcon.port=$rconPort", "rcon.password=$rconPassword", 'motd=MinecraftDevKit isolated protocol probe', 'max-players=4', 'spawn-protection=0', 'view-distance=4', 'simulation-distance=4') |
    Set-Content -LiteralPath (Join-Path $serverDirectory 'server.properties') -Encoding ascii

$packageFile = Join-Path $PSScriptRoot 'package.json'
Copy-Item -LiteralPath $packageFile -Destination (Join-Path $clientDirectory 'package.json') -Force
$npm = Get-Command npm -ErrorAction SilentlyContinue
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $npm -or -not $node) { throw 'Node.js and npm are required only for this opt-in protocol probe.' }
Push-Location $clientDirectory
try { & $npm.Source install --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { throw "npm install failed ($LASTEXITCODE)." } }
finally { Pop-Location }

$started = [DateTime]::UtcNow
$process = $null
$shutdown = 'NOT VERIFIED'
$protocolPath = Join-Path $resultsDirectory 'protocol-results.json'
$probeStatePath = Join-Path $pluginDirectory 'MdkRuntimeProbe/completion-protocol-state.json'
$stdoutPath = Join-Path $serverDirectory 'stdout.log'
$stderrPath = Join-Path $serverDirectory 'stderr.log'
$failure = $null
try {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $javaPath
    $paperControls = if ($Platform -eq 'paper') { ' -Dmdk.protocol.directCloud=true' + $(if ($CommandBackend -eq 'native') { ' -Dmdk.protocol.paperNative=true' } else { '' }) + $(if ($PermissionAwareCompletions) { ' -Dmdk.protocol.permissionAwareCompletions=true' } else { '' }) } else { '' }
    $startInfo.Arguments = "-Xms512M -Xmx2G -Dmdk.runtime.platform=$Platform$paperControls -jar server.jar --nogui"
    $startInfo.WorkingDirectory = $serverDirectory
    $startInfo.UseShellExecute = $false; $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true; $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw 'Could not start the isolated server.' }
    $outTask = $process.StandardOutput.ReadToEndAsync(); $errTask = $process.StandardError.ReadToEndAsync()
    $ready = [Diagnostics.Stopwatch]::StartNew()
    while ($ready.Elapsed.TotalSeconds -lt 300) {
        if ($process.HasExited) { throw "Server exited before readiness with code $($process.ExitCode)." }
        $latest = Join-Path $serverDirectory 'logs/latest.log'
        if ((Test-Path $latest) -and (Select-String -LiteralPath $latest -Pattern 'Done \([^\r\n]+\)!' -Quiet)) { break }
        Start-Sleep -Milliseconds 500
    }
    if ($ready.Elapsed.TotalSeconds -ge 300) { throw 'Server readiness timed out.' }
    $connectedRcon = $false
    for ($attempt = 0; $attempt -lt 30 -and -not $connectedRcon; $attempt++) {
        try { $null = Invoke-Rcon $rconPort $rconPassword 'list'; $connectedRcon = $true } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $connectedRcon) { throw 'Local RCON did not become ready.' }

    $env:MDK_PROTOCOL_HOST = '127.0.0.1'; $env:MDK_PROTOCOL_PORT = "$gamePort"
    $env:MDK_PROTOCOL_PROFILES = ($profileList -join ',')
    $env:MDK_PROTOCOL_REPORT = $protocolPath
    $env:MDK_PROTOCOL_STATE = $probeStatePath
    $env:NODE_PATH = Join-Path $clientDirectory 'node_modules'
    $env:MDK_PROTOCOL_TIMEOUT_MS = '2500'
    $clientInfo = [Diagnostics.ProcessStartInfo]::new()
    $clientInfo.FileName = $node.Source
    $clientInfo.Arguments = '"' + (Join-Path $PSScriptRoot 'client.js') + '"'
    $clientInfo.WorkingDirectory = $repoRoot
    $clientInfo.UseShellExecute = $false; $clientInfo.CreateNoWindow = $true
    $clientInfo.RedirectStandardOutput = $true; $clientInfo.RedirectStandardError = $true
    $clientInfo.EnvironmentVariables['MDK_PROTOCOL_HOST'] = '127.0.0.1'
    $clientInfo.EnvironmentVariables['MDK_PROTOCOL_PORT'] = "$gamePort"
    $clientInfo.EnvironmentVariables['MDK_PROTOCOL_PROFILES'] = ($profileList -join ',')
    $clientInfo.EnvironmentVariables['MDK_PROTOCOL_REPORT'] = $protocolPath
    $clientInfo.EnvironmentVariables['MDK_PROTOCOL_STATE'] = $probeStatePath
    $clientInfo.EnvironmentVariables['MDK_PROTOCOL_TIMEOUT_MS'] = '2500'
    $clientInfo.EnvironmentVariables['NODE_PATH'] = Join-Path $clientDirectory 'node_modules'
    $clientProc = [Diagnostics.Process]::new(); $clientProc.StartInfo = $clientInfo
    if (-not $clientProc.Start()) { throw 'Could not start protocol test client.' }
    $clientOut = $clientProc.StandardOutput.ReadToEndAsync(); $clientErr = $clientProc.StandardError.ReadToEndAsync()
    if (-not $clientProc.WaitForExit($ClientTimeoutSeconds * 1000)) { $clientProc.Kill(); $clientProc.WaitForExit(); throw "Protocol client exceeded $ClientTimeoutSeconds seconds." }
    $clientProc.WaitForExit()
    $clientText = $clientOut.Result; $clientErrorText = $clientErr.Result
    Set-Content -LiteralPath (Join-Path $resultsDirectory 'client.stdout.json') -Value $clientText -Encoding utf8
    Set-Content -LiteralPath (Join-Path $resultsDirectory 'client.stderr.log') -Value $clientErrorText -Encoding utf8
    if (-not (Test-Path $protocolPath)) { throw 'Protocol client did not create its structured result.' }
    $protocol = Get-Content -LiteralPath $protocolPath -Raw | ConvertFrom-Json
    if ($protocol.profiles.Count -ne $profileList.Count) { throw 'Protocol client did not complete every requested permission profile.' }
}
catch { $failure = $_.Exception.Message }
finally {
    if ($process -and -not $process.HasExited) {
        try { $null = Invoke-Rcon $rconPort $rconPassword 'stop' } catch { }
        if ($process.WaitForExit(45000)) { $shutdown = 'PASS' }
        else { $ownedPid = $process.Id; try { $process.Kill(); $process.WaitForExit(10000) | Out-Null; $shutdown = 'FAIL (owned process forced after timeout)' } catch { $shutdown = "FAIL (owned PID $ownedPid cleanup failed: $($_.Exception.Message))" } }
        try { Set-Content -LiteralPath $stdoutPath -Value $outTask.Result -Encoding utf8 } catch { }
        try { Set-Content -LiteralPath $stderrPath -Value $errTask.Result -Encoding utf8 } catch { }
        $process.Dispose()
    }
}

$probeState = $null
if (Test-Path -LiteralPath $probeStatePath) { try { $probeState = Get-Content -LiteralPath $probeStatePath -Raw | ConvertFrom-Json } catch { } }
$checks = [System.Collections.Generic.List[object]]::new()
function Get-ObservedSuggestions($Query) {
    if ($Query -and $Query.response) { return @($Query.response | ForEach-Object { $_.match }) }
    if ($Query -and $Query.eventObservation) { return @($Query.eventObservation.suggestions) }
    return @()
}
if ($protocol) {
    foreach ($profile in $protocol.profiles) {
        $root = $profile.queries | Where-Object text -eq '/devkittest ' | Select-Object -First 1
        $rootInspectPrefix = $profile.queries | Where-Object text -eq '/devkittest i' | Select-Object -First 1
        $rootModifyPrefix = $profile.queries | Where-Object text -eq '/devkittest m' | Select-Object -First 1
        $admin = $profile.queries | Where-Object text -eq '/devkittest admin ' | Select-Object -First 1
        $region = $profile.queries | Where-Object text -eq '/devkittest region ' | Select-Object -First 1
        $regionAlias = $profile.queries | Where-Object text -eq '/dktest region ' | Select-Object -First 1
        $inspectDynamic = $profile.queries | Where-Object text -eq '/devkittest inspect ' | Select-Object -First 1
        $allowInspect = $profile.profile -in @('operator', 'inspect', 'all', 'broad-deny')
        $allowModify = $profile.profile -in @('operator', 'modify', 'all', 'broad-deny')
        $allowRegion = $profile.profile -in @('operator', 'region', 'region-admin', 'all', 'broad-deny')
        $allowAdmin = $profile.profile -in @('operator', 'admin', 'all', 'broad-deny')
        $rootValues = Get-ObservedSuggestions $root
        $rootExpected = @('public')
        if ($allowInspect) { $rootExpected += 'inspect' }
        if ($allowModify) { $rootExpected += 'modify' }
        # Paper's Brigadier command tree should expose intermediate paths only when an
        # executable descendant is accessible. Spigot's Bukkit tab-completion path does not
        # expose Cloud's filtered Brigadier tree and retains its separately observed behavior.
        if ($Platform -eq 'spigot') { $rootExpected += @('admin', 'region') }
        else {
            if ($allowAdmin) { $rootExpected += 'admin' }
            if ($allowRegion) { $rootExpected += 'region' }
        }
        $rootUnauthorized = @($rootValues | Where-Object { $_ -in @('public', 'inspect', 'modify', 'admin', 'region') -and $_ -notin $rootExpected }).Count -gt 0
        $rootObserved = $root.status -eq 'PASS' -or $root.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET'
        $checks.Add([pscustomobject]@{ name = "explicit root permission filter ($($profile.profile))"; status = $(if ($rootUnauthorized) { 'FAIL' } elseif ($rootObserved) { 'PASS' } else { 'NOT VERIFIED' }); detail = "query=/devkittest ; expected=$($rootExpected -join ','); observed=$($rootValues -join ','); outcome=$($root.outcome)" })
        $inspectPrefixValues = Get-ObservedSuggestions $rootInspectPrefix; $modifyPrefixValues = Get-ObservedSuggestions $rootModifyPrefix
        $prefixUnauthorized = ((@($inspectPrefixValues) -contains 'inspect') -and -not $allowInspect) -or
            ((@($modifyPrefixValues) -contains 'modify') -and -not $allowModify)
        $prefixObserved = ($rootInspectPrefix.status -eq 'PASS' -or $rootInspectPrefix.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET') -and
            ($rootModifyPrefix.status -eq 'PASS' -or $rootModifyPrefix.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET')
        $checks.Add([pscustomobject]@{ name = "explicit prefixed root permission filter ($($profile.profile))"; status = $(if ($prefixUnauthorized) { 'FAIL' } elseif ($prefixObserved) { 'PASS' } else { 'NOT VERIFIED' }); detail = "inspectPrefix=$($inspectPrefixValues -join ','); modifyPrefix=$($modifyPrefixValues -join ',')" })
        $visibleLeaves = @($profile.commandTree.path)
        $allowAdminReload = $profile.profile -in @('operator', 'admin', 'all', 'broad-deny')
        $allowAdminReset = $profile.profile -in @('operator', 'admin', 'all')
        $allowRegionLeaves = switch ($profile.profile) {
            'region' { @('info', 'list') }
            { $_ -in @('operator', 'region-admin', 'all', 'broad-deny') } { @('info', 'list', 'add', 'delete') }
            default { @() }
        }
        $protectedTreeExpectations = @(
            @{ path = 'devkittest inspect target'; allowed = $allowInspect },
            @{ path = 'devkittest modify target'; allowed = $allowModify },
            @{ path = 'devkittest admin reload'; allowed = $allowAdminReload },
            @{ path = 'devkittest admin reset'; allowed = $allowAdminReset },
            @{ path = 'devkittest region info region'; allowed = ('info' -in $allowRegionLeaves) },
            @{ path = 'devkittest region list'; allowed = ('list' -in $allowRegionLeaves) },
            @{ path = 'devkittest region add region'; allowed = ('add' -in $allowRegionLeaves) },
            @{ path = 'devkittest region delete region'; allowed = ('delete' -in $allowRegionLeaves) }
        )
        $treeMismatches = @($protectedTreeExpectations | Where-Object { ($_.path -in $visibleLeaves) -ne $_.allowed })
        $checks.Add([pscustomobject]@{ name = "delivered command tree protected executable paths ($($profile.profile))";
            status = if ($Platform -eq 'spigot') { 'NOT APPLICABLE' } elseif ($treeMismatches.Count) { 'FAIL' } else { 'PASS' };
            detail = if ($Platform -eq 'spigot') { 'Spigot uses the Bukkit command-completion path; its delivered tree is a generic args fallback, not Cloud Brigadier leaves. See TabCompleteEvent evidence.' } else { "visible=$(@($visibleLeaves | Where-Object { $_ -like 'devkittest*' }) -join ';'); expected-access paths=$(@($protectedTreeExpectations | Where-Object allowed | ForEach-Object path) -join ';'); mismatches=$($treeMismatches.Count)" } })
        $allowedRegion = switch ($profile.profile) {
            'region' { @('info', 'list') }
            { $_ -in @('operator', 'region-admin', 'all', 'broad-deny') } { @('add', 'delete', 'info', 'list') }
            default { @() }
        }
        $regionValues = Get-ObservedSuggestions $region; $regionAliasValues = Get-ObservedSuggestions $regionAlias
        $regionUnauthorized = @($regionValues | Where-Object { $_ -in @('add', 'delete', 'info', 'list') -and $_ -notin $allowedRegion }).Count -gt 0
        $regionObserved = $region.status -eq 'PASS' -or $region.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET'
        $checks.Add([pscustomobject]@{ name = "explicit nested region permission filter ($($profile.profile))"; status = $(if ($regionUnauthorized) { 'FAIL' } elseif ($regionObserved) { 'PASS' } else { 'NOT VERIFIED' }); detail = "query=/devkittest region ; allowed=$($allowedRegion -join ','); observed=$($regionValues -join ','); outcome=$($region.outcome)" })
        $regionAliasLeaks = @($regionAliasValues | Where-Object { $_ -in @('add', 'delete', 'info', 'list') -and $_ -notin $allowedRegion }).Count -gt 0
        $aliasObserved = $regionAlias.status -eq 'PASS' -or $regionAlias.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET'
        $checks.Add([pscustomobject]@{ name = "explicit alias nested permission filter ($($profile.profile))"; status = $(if ($regionAliasLeaks) { 'FAIL' } elseif ($aliasObserved) { 'PASS' } else { 'NOT VERIFIED' }); detail = "query=/dktest region ; observed=$($regionAliasValues -join ','); outcome=$($regionAlias.outcome)" })
        $allowedAdmin = switch ($profile.profile) {
            { $_ -in @('operator', 'admin') } { @('reload', 'reset') }
            'broad-deny' { @('reload') }
            { $_ -in @('operator', 'all') } { @('reload', 'reset') }
            default { @() }
        }
        $adminValues = Get-ObservedSuggestions $admin
        $adminLeaks = @($adminValues | Where-Object { $_ -in @('reload', 'reset') -and $_ -notin $allowedAdmin }).Count -gt 0
        $adminObserved = $admin.status -eq 'PASS' -or $admin.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET'
        $checks.Add([pscustomobject]@{ name = "explicit nested admin permission filter ($($profile.profile))"; status = $(if ($adminLeaks) { 'FAIL' } elseif ($adminObserved) { 'PASS' } else { 'NOT VERIFIED' }); detail = "query=/devkittest admin ; allowed=$($allowedAdmin -join ','); observed=$($adminValues -join ','); outcome=$($admin.outcome)" })
        $dynamicCases = @(
            @{ query = '/devkittest inspect '; provider = 'inspect'; private = 'private-target'; allowed = $allowInspect },
            @{ query = '/devkittest modify '; provider = 'modify'; private = 'private-modify-target'; allowed = $allowModify },
            @{ query = '/devkittest region info '; provider = 'region-info'; private = 'restricted-region'; allowed = ($profile.profile -in @('operator', 'region', 'region-admin', 'all', 'broad-deny')) },
            @{ query = '/devkittest region delete '; provider = 'region-delete'; private = 'restricted-region'; allowed = ($profile.profile -in @('operator', 'region-admin', 'all', 'broad-deny')) }
        )
        foreach ($dynamicCase in $dynamicCases) {
            $dynamicQuery = $profile.queries | Where-Object text -eq $dynamicCase.query | Select-Object -First 1
            $dynamicValues = Get-ObservedSuggestions $dynamicQuery
            $count = 0
            if ($profile.instrumentation -match ("providers=\{[^}]*" + [regex]::Escape($dynamicCase.provider) + ":(\d+)")) { $count = [int]$Matches[1] }
            $leaked = @($dynamicValues | Where-Object { $_ -eq $dynamicCase.private }).Count -gt 0 -and -not $dynamicCase.allowed
            $calledIncorrectly = ($count -gt 0) -and -not $dynamicCase.allowed
            $notCalled = ($count -eq 0) -and $dynamicCase.allowed
            $requestSent = $dynamicQuery -and $dynamicQuery.transactionId -gt 0
            $dynamicStatus = if ($leaked -or $calledIncorrectly -or $notCalled) { 'FAIL' }
                elseif (-not $dynamicCase.allowed -and $requestSent -and $count -eq 0 -and $dynamicValues.Count -eq 0) { 'PASS' }
                elseif ($dynamicCase.allowed -and ($dynamicQuery.status -eq 'PASS' -or $dynamicQuery.outcome -eq 'EMPTY_COMPLETION_EVENT_NO_PACKET')) { 'PASS' }
                else { 'NOT VERIFIED' }
            $checks.Add([pscustomobject]@{ name = "dynamic provider authorization $($dynamicCase.provider) ($($profile.profile))"; status = $dynamicStatus; detail = "allowed=$($dynamicCase.allowed); count=$count; observed=$($dynamicValues -join ','); outcome=$($dynamicQuery.outcome)" })
        }
        if ($Platform -eq 'paper') {
            $cloudOnly = $profile.queries | Where-Object text -eq '/cloudonlyprobe ' | Select-Object -First 1
            $cloudOnlyValues = Get-ObservedSuggestions $cloudOnly
            $cloudOnlyAdmin = $profile.queries | Where-Object text -eq '/cloudonlyprobe admin ' | Select-Object -First 1
            $cloudOnlyRegion = $profile.queries | Where-Object text -eq '/cloudonlyprobe region ' | Select-Object -First 1
            $cloudOnlyAdminValues = Get-ObservedSuggestions $cloudOnlyAdmin
            $cloudOnlyRegionValues = Get-ObservedSuggestions $cloudOnlyRegion
            $cloudOnlyAllowed = $profile.profile -in @('operator', 'all')
            $directExpectedRoot = if ($cloudOnlyAllowed) { @('public', 'inspect', 'modify', 'admin', 'region', 'secret') } else { @('public') }
            $directRootLeaks = @($cloudOnlyValues | Where-Object { $_ -in @('secret', 'inspect', 'modify', 'admin', 'region') -and $_ -notin $directExpectedRoot }).Count -gt 0
            $directExpectedAdmin = if ($cloudOnlyAllowed) { @('reload', 'reset') } else { @() }
            $directExpectedRegion = if ($cloudOnlyAllowed) { @('info', 'list', 'add', 'delete') } else { @() }
            $directNestedLeaks = @($cloudOnlyAdminValues | Where-Object { $_ -in @('reload', 'reset') -and $_ -notin $directExpectedAdmin }).Count -gt 0 -or
                @($cloudOnlyRegionValues | Where-Object { $_ -in @('info', 'list', 'add', 'delete') -and $_ -notin $directExpectedRegion }).Count -gt 0
            $directQueriesObserved = $cloudOnly.transactionId -gt 0 -and $cloudOnlyAdmin.transactionId -gt 0 -and $cloudOnlyRegion.transactionId -gt 0
            $directHasExpected = $cloudOnlyAllowed -and 'secret' -in $cloudOnlyValues -and 'reload' -in $cloudOnlyAdminValues -and 'delete' -in $cloudOnlyRegionValues
            # The direct Cloud root is an intentional control: protected literals should still be
            # visible there because the optional filter is scoped to MDK-owned definitions only.
            $directStatus = if ($cloudOnlyAllowed -and ($directRootLeaks -or $directNestedLeaks)) { 'FAIL' }
                elseif ($cloudOnlyAllowed -and $directHasExpected) { 'PASS' }
                elseif (-not $cloudOnlyAllowed -and $directQueriesObserved -and $directRootLeaks) { 'PASS' }
                else { 'NOT VERIFIED' }
            $checks.Add([pscustomobject]@{ name = "direct Cloud-only explicit completion control ($($profile.profile))"; status = $directStatus; detail = "allowed=$cloudOnlyAllowed; expected unfiltered raw control for denied profile=$(-not $cloudOnlyAllowed); root=$($cloudOnlyValues -join ','); admin=$($cloudOnlyAdminValues -join ','); region=$($cloudOnlyRegionValues -join ',')" })
            $cloudProtectedPaths = @('cloudonlyprobe secret', 'cloudonlyprobe inspect', 'cloudonlyprobe modify',
                'cloudonlyprobe admin reload', 'cloudonlyprobe admin reset', 'cloudonlyprobe region info',
                'cloudonlyprobe region list', 'cloudonlyprobe region add', 'cloudonlyprobe region delete')
            $directTreePaths = @($visibleLeaves | Where-Object { $_ -in $cloudProtectedPaths })
            $directTreeStatus = if ($cloudOnlyAllowed -and $directTreePaths.Count -eq $cloudProtectedPaths.Count) { 'PASS' }
                elseif (-not $cloudOnlyAllowed -and $directTreePaths.Count -eq 0) { 'PASS' } else { 'FAIL' }
            $checks.Add([pscustomobject]@{ name = "direct Cloud-only delivered tree control ($($profile.profile))"; status = $directTreeStatus; detail = "protectedPaths=$($directTreePaths -join ','); expectedCount=$(if ($cloudOnlyAllowed) { $cloudProtectedPaths.Count } else { 0 })" })
        }
        $handlerCount = -1
        if ($profile.instrumentation -match 'handlers=(\d+)') { $handlerCount = [int]$Matches[1] }
        $checks.Add([pscustomobject]@{ name = "completion request does not execute handlers ($($profile.profile))"; status = $(if ($handlerCount -eq 0) { 'PASS' } else { 'FAIL' }); detail = $profile.instrumentation })
        $expectedExecutionCount = if ($profile.execution.allowed) { 1 } else { 0 }
        $observedExecutionCount = -1
        if ($profile.execution.instrumentation -match 'handlers=(\d+)') { $observedExecutionCount = [int]$Matches[1] }
        $checks.Add([pscustomobject]@{ name = "command execution authorization ($($profile.profile))"; status = $(if ($observedExecutionCount -eq $expectedExecutionCount) { 'PASS' } else { 'FAIL' }); detail = "command=$($profile.execution.command); expected handler calls=$expectedExecutionCount; observed=$observedExecutionCount; $($profile.execution.instrumentation)" })
        if ($profile.profile -eq 'broad-deny') {
            $explicitDenialObserved = $profile.profileMessage -match 'adminReset=False'
            $checks.Add([pscustomobject]@{ name = 'effective Bukkit permission explicit denial'; status = $(if ($explicitDenialObserved -and $observedExecutionCount -eq 0) { 'PASS' } else { 'FAIL' }); detail = "profile=$($profile.profileMessage); post-attempt=$($profile.execution.instrumentation)" })
        }
    }
}
$report = [ordered]@{
    runId = $runId; startedUtc = $started.ToString('o'); completedUtc = [DateTime]::UtcNow.ToString('o')
    platform = $Platform; minecraftVersion = '1.21.11'; commandBackend = $CommandBackend; permissionAwareCompletions = [bool]$PermissionAwareCompletions; serverArtifactSha256 = $actualServerHash
    serverJar = [IO.Path]::GetFileName($ServerJar); onlineMode = $false; authenticatedClient = $false
    gitRevision = (& git -C $repoRoot rev-parse HEAD).Trim()
    pluginArtifacts = @(
        @{ name = 'integration'; sha256 = (Get-FileHash (Join-Path $pluginDirectory 'IntegrationExample.jar') -Algorithm SHA256).Hash.ToLowerInvariant() },
        @{ name = 'storage'; sha256 = (Get-FileHash (Join-Path $pluginDirectory 'StorageExample.jar') -Algorithm SHA256).Hash.ToLowerInvariant() },
        @{ name = 'runtime-probe'; sha256 = (Get-FileHash (Join-Path $pluginDirectory 'MdkRuntimeProbe.jar') -Algorithm SHA256).Hash.ToLowerInvariant() }
    )
    java = ($javaInfo -split "`r?`n")[0].Trim(); sessionId = [Guid]::NewGuid().ToString()
    status = if ($failure) { 'FAIL' } elseif (@($checks | Where-Object status -eq 'FAIL').Count) { 'FAIL' } elseif (@($checks | Where-Object status -eq 'NOT VERIFIED').Count -or -not $checks.Count) { 'NOT VERIFIED' } else { 'PASS' }
    shutdown = $shutdown; portReleased = (Test-PortReleased $gamePort)
    failure = $failure; checks = @($checks); client = $protocol; serverProbeState = $probeState
    evidenceDirectory = $serverDirectory
}
$reportPath = Join-Path $resultsDirectory 'report.json'
$report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $reportPath -Encoding utf8
$inventory = Get-ChildItem -LiteralPath $serverDirectory -File -Recurse | Where-Object FullName -ne (Join-Path $resultsDirectory 'checksums.json') | ForEach-Object {
    [pscustomobject]@{ path = $_.FullName.Substring($serverDirectory.Length).TrimStart('\'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
}
[pscustomobject]@{ algorithm = 'SHA-256'; files = @($inventory) } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $resultsDirectory 'checksums.json') -Encoding utf8
Write-Host "Protocol completion report: $reportPath"
Write-Host "Status=$($report.status) shutdown=$shutdown portReleased=$($report.portReleased) unauthenticated=$(-not $report.authenticatedClient)"
if ($failure) { Write-Error $failure }
if ($report.status -eq 'FAIL') { exit 1 }
