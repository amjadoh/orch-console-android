# sirfari-agent on Amjad's Windows laptop: every minute, reports the laptop's Claude/Codex sessions and their state to Sirfari
# over Tailscale (the Tailscale identity proves it is Amjad's device, so no secret is stored). Data only: it never receives commands.
#   -Once: run once   -DryRun <file>: write the report to a file instead of sending it   (ASCII only, see hook.ps1.)
param([switch]$Once, [string]$DryRun = '')
$ErrorActionPreference = 'Stop'
$server = 'https://amjad-server.tail6b6055.ts.net'
$base = if ($env:SIRFARI_HOME) { $env:SIRFARI_HOME } else { Join-Path $env:LOCALAPPDATA 'Sirfari' }
New-Item -ItemType Directory -Force -Path (Join-Path $base 'events') | Out-Null
$idFile = Join-Path $base 'device-id'
if (-not (Test-Path $idFile)) { [IO.File]::WriteAllText($idFile, [guid]::NewGuid().ToString('N')) }
$deviceId = ([IO.File]::ReadAllText($idFile)).Trim()
$log = Join-Path $base 'agent.log'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

function Get-Report {
  $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $sessions = @()
  foreach ($f in @(Get-ChildItem -LiteralPath (Join-Path $base 'events') -Filter '*.json' -ErrorAction SilentlyContinue)) {
    try {
      $e = [IO.File]::ReadAllText($f.FullName) | ConvertFrom-Json
      # untouched for two days = probably closed without SessionEnd: drop it
      # Codex only reports finished turns, so its entries expire sooner (6h); Claude after two days
      $maxAge = if ([string]$e.tool -eq 'codex') { 21600000 } else { 172800000 }
      if ($now - [int64]$e.at -gt $maxAge) { Remove-Item -LiteralPath $f.FullName -Force; continue }
      $sessions += [ordered]@{ sessionId = [string]$e.sessionId; tool = [string]$e.tool; project = $e.project; state = [string]$e.state; at = [int64]$e.at }
    } catch {}
  }
  $procs = @()
  foreach ($p in @(Get-Process -Name 'claude', 'codex' -ErrorAction SilentlyContinue)) {
    $age = $null
    try { $age = [math]::Round(((Get-Date) - $p.StartTime).TotalSeconds) } catch {}
    $procs += [ordered]@{ tool = $p.ProcessName.ToLower(); rssMb = [math]::Round($p.WorkingSet64 / 1MB); ageSec = $age }
  }
  return [ordered]@{ device = [ordered]@{ id = $deviceId; name = $env:COMPUTERNAME; os = 'windows' }; sessions = $sessions; processes = $procs }
}

while ($true) {
  try {
    $body = Get-Report | ConvertTo-Json -Depth 6 -Compress
    if ($DryRun) {
      [IO.File]::WriteAllText($DryRun, $body, (New-Object Text.UTF8Encoding $false))
    } else {
      Invoke-RestMethod -Uri "$server/api/agent/report" -Method Post -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType 'application/json' -Headers @{ Origin = $server; 'X-Orch' = '1' } -TimeoutSec 15 -UseBasicParsing | Out-Null
    }
  } catch {
    try {
      if ((Test-Path $log) -and (Get-Item $log).Length -gt 100KB) { Remove-Item $log -Force }
      Add-Content -LiteralPath $log -Value "$(Get-Date -Format s) $($_.Exception.Message)"
    } catch {}
  }
  if ($Once -or $DryRun) { break }
  Start-Sleep -Seconds 60
}
