# Sirfari: Claude Code hook on Amjad's Windows laptop. Writes the session state to a local file only (fast, no network).
# SessionStart/Stop = ready, UserPromptSubmit/PostToolUse = working, PermissionRequest/Notification = needs, SessionEnd = ended.
# Stop with background tasks still running = working. Any error = exit quietly so the session is never disturbed.
# (ASCII only on purpose: Windows PowerShell 5.1 misreads non-ASCII .ps1 files without a BOM.)
$ErrorActionPreference = 'SilentlyContinue'
try {
  $raw = [Console]::In.ReadToEnd()
  $j = $raw | ConvertFrom-Json
  $ev = [string]$j.hook_event_name
  $state = $null
  switch ($ev) {
    'SessionStart' { $state = 'ready' }
    'Stop' {
      $state = 'ready'
      foreach ($p in $j.PSObject.Properties) {
        if ($p.Name -match 'background' -and $null -ne $p.Value) {
          $v = $p.Value
          if (($v -is [array] -and $v.Count -gt 0) -or ($v -is [bool] -and $v) -or (($v -is [int] -or $v -is [long]) -and $v -gt 0) -or ($v -is [pscustomobject] -and @($v.PSObject.Properties).Count -gt 0)) { $state = 'working' }
        }
      }
    }
    'PermissionRequest' { $state = 'needs' }
    'UserPromptSubmit' { $state = 'working' }
    'PostToolUse' { $state = 'working' }
    'Notification' { if ("$($j.notification_type) $($j.message)" -notmatch 'waiting for your input|idle') { $state = 'needs' } }
    'SessionEnd' { $state = 'ended' }
  }
  $sid = ([string]$j.session_id) -replace '[^A-Za-z0-9_-]', ''
  if ($state -and $sid) {
    $base = if ($env:SIRFARI_HOME) { $env:SIRFARI_HOME } else { Join-Path $env:LOCALAPPDATA 'Sirfari' }
    $dir = Join-Path $base 'events'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $file = Join-Path $dir "$sid.json"
    if ($state -eq 'ended') {
      Remove-Item -LiteralPath $file -Force
    } else {
      $project = if ($j.cwd) { Split-Path -Leaf ([string]$j.cwd) } else { $null }
      $doc = [ordered]@{ sessionId = $sid; tool = 'claude'; project = $project; state = $state; at = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() } | ConvertTo-Json -Compress
      [IO.File]::WriteAllText("$file.tmp", $doc, (New-Object Text.UTF8Encoding $false))
      Move-Item -LiteralPath "$file.tmp" -Destination $file -Force
    }
  }
} catch {}
exit 0
