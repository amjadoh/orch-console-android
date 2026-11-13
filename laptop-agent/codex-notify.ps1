# سيرفري: notify حق Codex على لابتوب أمجد (ويندوز). Codex يناديه لما يخلص رد (agent-turn-complete) ويعطيه JSON كآخر معامل.
# يكتب «جاهزة» للمحادثة في ملف محلي بس. أي خطأ = نطلع بصمت.
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
