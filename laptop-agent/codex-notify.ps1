# Sirfari: Codex notify on Amjad's Windows laptop. Codex calls it when a turn completes (agent-turn-complete), JSON as the last argument.
# Writes "ready" for the thread to a local file only. Any error = exit quietly. (ASCII only, see hook.ps1.)
$ErrorActionPreference = 'SilentlyContinue'
try {
  $j = $args[-1] | ConvertFrom-Json
  if ([string]$j.type -eq 'agent-turn-complete') {
    $sid = ([string]$j.'thread-id') -replace '[^A-Za-z0-9_-]', ''
    if (-not $sid) { $sid = ([string]$j.'turn-id') -replace '[^A-Za-z0-9_-]', '' }
    if ($sid) {
      $base = if ($env:SIRFARI_HOME) { $env:SIRFARI_HOME } else { Join-Path $env:LOCALAPPDATA 'Sirfari' }
      $dir = Join-Path $base 'events'
      New-Item -ItemType Directory -Force -Path $dir | Out-Null
      $file = Join-Path $dir "codex-$sid.json"
      $project = if ($j.cwd) { Split-Path -Leaf ([string]$j.cwd) } else { $null }
      $doc = [ordered]@{ sessionId = "codex-$sid"; tool = 'codex'; project = $project; state = 'ready'; at = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() } | ConvertTo-Json -Compress
      [IO.File]::WriteAllText("$file.tmp", $doc, (New-Object Text.UTF8Encoding $false))
      Move-Item -LiteralPath "$file.tmp" -Destination $file -Force
    }
  }
} catch {}
exit 0
