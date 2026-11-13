# Install sirfari-agent on Amjad's Windows laptop, once, no admin rights needed:
#   irm https://amjad-server.tail6b6055.ts.net/agent/install.ps1 | iex
# 1) downloads the scripts from Sirfari (over Tailscale) to %LOCALAPPDATA%\Sirfari
# 2) adds Claude Code hooks to %USERPROFILE%\.claude\settings.json (backup first, keeps everything that is there)
# 3) adds Codex notify to %USERPROFILE%\.codex\config.toml (only if there is no notify yet)
# 4) starts the agent now and at every logon (scheduled task, or a Startup shortcut if the task is refused)
# Testing: SIRFARI_SOURCE = folder with the scripts instead of downloading; SIRFARI_NO_START=1 does not start the agent.
# (ASCII only on purpose: Windows PowerShell 5.1 and `irm | iex` both read it correctly.)
$ErrorActionPreference = 'Stop'
$server = 'https://amjad-server.tail6b6055.ts.net'
$base = if ($env:SIRFARI_HOME) { $env:SIRFARI_HOME } else { Join-Path $env:LOCALAPPDATA 'Sirfari' }
$utf8 = New-Object Text.UTF8Encoding $false
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
New-Item -ItemType Directory -Force -Path (Join-Path $base 'events') | Out-Null

Write-Host 'Sirfari: downloading the scripts...'
foreach ($name in 'hook.ps1', 'codex-notify.ps1', 'agent.ps1') {
  $dest = Join-Path $base $name
  if ($env:SIRFARI_SOURCE) { Copy-Item -LiteralPath (Join-Path $env:SIRFARI_SOURCE $name) -Destination $dest -Force }
  else { Invoke-WebRequest -Uri "$server/agent/$name" -OutFile $dest -UseBasicParsing }
}

# -- Claude Code: hooks --
$claudeDir = Join-Path $env:USERPROFILE '.claude'
$settingsPath = Join-Path $claudeDir 'settings.json'
New-Item -ItemType Directory -Force -Path $claudeDir | Out-Null
$hookCmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $base 'hook.ps1')`""
$settings = [pscustomobject]@{}
if (Test-Path $settingsPath) {
  Copy-Item -LiteralPath $settingsPath -Destination "$settingsPath.bak-sirfari-$(Get-Date -Format yyyyMMddHHmmss)" -Force
  $text = [IO.File]::ReadAllText($settingsPath)
  if ($text.Trim()) { $settings = $text | ConvertFrom-Json }
}
if (-not ($settings.PSObject.Properties.Name -contains 'hooks') -or $null -eq $settings.hooks) {
  $settings | Add-Member -NotePropertyName hooks -NotePropertyValue ([pscustomobject]@{}) -Force
}
$added = 0
# No PostToolUse on Windows: starting PowerShell after every tool call would slow Claude down.
foreach ($ev in 'SessionStart', 'UserPromptSubmit', 'PermissionRequest', 'Notification', 'Stop', 'SessionEnd') {
  $list = @()
  if ($settings.hooks.PSObject.Properties.Name -contains $ev) { $list = @($settings.hooks.$ev) }
  $exists = $false
  foreach ($g in $list) { foreach ($h in @($g.hooks)) { if ([string]$h.command -like '*Sirfari*hook.ps1*') { $exists = $true } } }
  if ($exists) { continue }
  $group = [ordered]@{}
  if ($ev -in 'PostToolUse', 'PermissionRequest') { $group.matcher = '*' }
  $group.hooks = @([pscustomobject]@{ type = 'command'; command = $hookCmd; timeout = 10 })
  $list += [pscustomobject]$group
  $settings.hooks | Add-Member -NotePropertyName $ev -NotePropertyValue @($list) -Force
  $added++
}
# Drop a Sirfari PostToolUse hook left by an older version (it slows Claude down on Windows)
if ($settings.hooks.PSObject.Properties.Name -contains 'PostToolUse') {
  $keep = @(@($settings.hooks.PostToolUse) | Where-Object { -not (@($_.hooks) | Where-Object { [string]$_.command -like '*Sirfari*hook.ps1*' }) })
  if ($keep.Count -eq 0) { $settings.hooks.PSObject.Properties.Remove('PostToolUse') } else { $settings.hooks | Add-Member -NotePropertyName PostToolUse -NotePropertyValue $keep -Force }
}
[IO.File]::WriteAllText($settingsPath, ($settings | ConvertTo-Json -Depth 20), $utf8)
Write-Host "Claude: added $added hooks (existing settings kept)."

# -- Codex: notify (a top-level key: must go before any [table]) --
$codexDir = Join-Path $env:USERPROFILE '.codex'
$codexCfg = Join-Path $codexDir 'config.toml'
New-Item -ItemType Directory -Force -Path $codexDir | Out-Null
$cfgText = if (Test-Path $codexCfg) { [IO.File]::ReadAllText($codexCfg) } else { '' }
if ($cfgText -match '(?m)^\s*notify\s*=') {
  if ($cfgText -notmatch 'codex-notify\.ps1') { Write-Host 'Codex: a notify is already set, left it as is (Codex state will show as unknown).' }
} else {
  if (Test-Path $codexCfg) { Copy-Item -LiteralPath $codexCfg -Destination "$codexCfg.bak-sirfari-$(Get-Date -Format yyyyMMddHHmmss)" -Force }
  $notifyPath = (Join-Path $base 'codex-notify.ps1')
  # TOML basic string: escape backslashes and double quotes (a path may contain ' or ")
  $tomlPath = '"' + ($notifyPath -replace '\\', '\\' -replace '"', '\"') + '"'
  $line = "notify = [`"powershell.exe`", `"-NoProfile`", `"-ExecutionPolicy`", `"Bypass`", `"-File`", $tomlPath]"
  [IO.File]::WriteAllText($codexCfg, "$line`r`n$cfgText", $utf8)
  Write-Host 'Codex: notify added.'
}

# -- start the agent now and at every logon --
$agentArgs = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$(Join-Path $base 'agent.ps1')`""
if (-not $env:SIRFARI_NO_START) {
  $ok = $false
  try {
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $agentArgs
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
    $set = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
    Register-ScheduledTask -TaskName 'Sirfari Agent' -Action $action -Trigger $trigger -Principal $principal -Settings $set -Force | Out-Null
    Start-ScheduledTask -TaskName 'Sirfari Agent'
    $ok = $true
    Write-Host 'Agent running now and at every logon (scheduled task).'
  } catch {}
  if (-not $ok) {
    $startup = [Environment]::GetFolderPath('Startup')
    $sh = New-Object -ComObject WScript.Shell
    $lnk = $sh.CreateShortcut((Join-Path $startup 'Sirfari Agent.lnk'))
    $lnk.TargetPath = 'powershell.exe'
    $lnk.Arguments = $agentArgs
    $lnk.WindowStyle = 7
    $lnk.Save()
    Start-Process powershell.exe -ArgumentList $agentArgs -WindowStyle Hidden
    Write-Host 'Agent running now and at every logon (Startup shortcut).'
  }
}
Write-Host 'Done. Open Sirfari > Server > Sessions: the laptop shows up within a minute. Sessions already open report from their next message.'
