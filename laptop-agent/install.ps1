# تركيب sirfari-agent على لابتوب أمجد (ويندوز)، مرة وحدة، بدون صلاحيات مدير:
#   irm https://amjad-server.tail6b6055.ts.net/agent/install.ps1 | iex
# 1) ينزّل السكربتات من سيرفري (عبر Tailscale) لـ %LOCALAPPDATA%\Sirfari
# 2) يضيف hooks لـ Claude Code في %USERPROFILE%\.claude\settings.json (نسخة احتياطية قبل، وما يمسح شي موجود)
# 3) يضيف notify لـ Codex في %USERPROFILE%\.codex\config.toml (لو ما فيه notify من قبل)
# 4) يشغّل الـ agent الحين ومع كل دخول للجهاز (مهمة مجدولة، أو اختصار في Startup لو المهمة ما انقبلت)
# للتجربة: SIRFARI_SOURCE = مجلد فيه السكربتات بدل التنزيل، وSIRFARI_NO_START=1 ما يشغّل الـ agent.
$ErrorActionPreference = 'Stop'
$server = 'https://amjad-server.tail6b6055.ts.net'
$base = if ($env:SIRFARI_HOME) { $env:SIRFARI_HOME } else { Join-Path $env:LOCALAPPDATA 'Sirfari' }
$utf8 = New-Object Text.UTF8Encoding $false
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
New-Item -ItemType Directory -Force -Path (Join-Path $base 'events') | Out-Null

Write-Host 'سيرفري: أنزّل السكربتات...'
foreach ($name in 'hook.ps1', 'codex-notify.ps1', 'agent.ps1') {
  $dest = Join-Path $base $name
  if ($env:SIRFARI_SOURCE) { Copy-Item -LiteralPath (Join-Path $env:SIRFARI_SOURCE $name) -Destination $dest -Force }
  else { Invoke-WebRequest -Uri "$server/agent/$name" -OutFile $dest -UseBasicParsing }
}

# ── Claude Code: hooks ──
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
foreach ($ev in 'SessionStart', 'UserPromptSubmit', 'PostToolUse', 'PermissionRequest', 'Notification', 'Stop', 'SessionEnd') {
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
[IO.File]::WriteAllText($settingsPath, ($settings | ConvertTo-Json -Depth 20), $utf8)
Write-Host "Claude: انضاف $added hook (الموجود ما تغيّر)."

# ── Codex: notify (مفتاح في أول الملف، قبل أي [قسم]) ──
$codexDir = Join-Path $env:USERPROFILE '.codex'
$codexCfg = Join-Path $codexDir 'config.toml'
New-Item -ItemType Directory -Force -Path $codexDir | Out-Null
$cfgText = if (Test-Path $codexCfg) { [IO.File]::ReadAllText($codexCfg) } else { '' }
if ($cfgText -match '(?m)^\s*notify\s*=') {
  if ($cfgText -notmatch 'codex-notify\.ps1') { Write-Host 'Codex: فيه notify من قبل، ما غيّرته (حالة Codex بتطلع «غير معروف»).' }
} else {
  if (Test-Path $codexCfg) { Copy-Item -LiteralPath $codexCfg -Destination "$codexCfg.bak-sirfari-$(Get-Date -Format yyyyMMddHHmmss)" -Force }
  $notifyPath = (Join-Path $base 'codex-notify.ps1')
  $line = "notify = [`"powershell.exe`", `"-NoProfile`", `"-ExecutionPolicy`", `"Bypass`", `"-File`", '$notifyPath']"
  [IO.File]::WriteAllText($codexCfg, "$line`r`n$cfgText", $utf8)
  Write-Host 'Codex: انضاف notify.'
}

# ── تشغيل الـ agent الحين ومع كل دخول ──
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
    Write-Host 'الـ agent يشتغل الحين ومع كل دخول (مهمة مجدولة).'
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
    Write-Host 'الـ agent يشتغل الحين ومع كل دخول (اختصار في Startup).'
  }
}
Write-Host '✅ خلص. افتح سيرفري ← السيرفر ← الجلسات: اللابتوب يطلع خلال دقيقة. الجلسات المفتوحة الحين تبلّغ من أول رسالة جاية.'
