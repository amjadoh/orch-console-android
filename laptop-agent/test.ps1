# اختبار sirfari-agent على ويندوز حقيقي (GitHub): الـ hook وnotify والتركيب (مرتين، بدون ما يمسح الموجود) والتقرير.
# السكربتات هنا نسخة من ops/console/public/agent في مستودع المنظّم (هناك تنخدم للابتوب)، والاختبار يتأكد إنها تشتغل على ويندوز.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Join-Path ([IO.Path]::GetTempPath()) ("sirfari-test-" + [guid]::NewGuid().ToString('N'))
$env:SIRFARI_HOME = Join-Path $root 'Sirfari'
$env:USERPROFILE = Join-Path $root 'user'
New-Item -ItemType Directory -Force -Path $env:SIRFARI_HOME, $env:USERPROFILE | Out-Null
$shell = (Get-Process -Id $PID).Path
$fail = 0
function Check($cond, $msg) { if ($cond) { Write-Host "ok  $msg" } else { Write-Host "FAIL $msg"; $script:fail++ } }
function Hook($json) { $json | & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'hook.ps1') }
function Ev($id) { $p = Join-Path $env:SIRFARI_HOME "events\$id.json"; if (Test-Path $p) { [IO.File]::ReadAllText($p) | ConvertFrom-Json } else { $null } }

Write-Host "shell: $shell $($PSVersionTable.PSVersion)"

# ── hook ──
Hook '{"hook_event_name":"UserPromptSubmit","session_id":"abc-1","cwd":"C:\\work\\masroof"}'
$e = Ev 'abc-1'
Check ($e.state -eq 'working' -and $e.project -eq 'masroof' -and $e.tool -eq 'claude') 'UserPromptSubmit = تشتغل'
Hook '{"hook_event_name":"Stop","session_id":"abc-1","background_tasks":[{"id":"b1"}]}'
Check ((Ev 'abc-1').state -eq 'working') 'Stop ومهام خلفية = تشتغل'
Hook '{"hook_event_name":"Stop","session_id":"abc-1","background_tasks":[]}'
Check ((Ev 'abc-1').state -eq 'ready') 'Stop = جاهزة'
Hook '{"hook_event_name":"PermissionRequest","session_id":"abc-1","tool_name":"Bash"}'
Check ((Ev 'abc-1').state -eq 'needs') 'PermissionRequest = تحتاجك'
Hook '{"hook_event_name":"Notification","session_id":"abc-1","message":"Claude is waiting for your input"}'
Check ((Ev 'abc-1').state -eq 'needs') 'Notification الانتظار ما يغيّر'
Hook '{"hook_event_name":"SessionStart","session_id":"x/../y"}'
Check ($null -ne (Ev 'xy')) 'معرّف الجلسة ينظّف'
Hook '{"hook_event_name":"SessionEnd","session_id":"xy"}'
Check ($null -eq (Ev 'xy')) 'SessionEnd = ينشال'
Hook 'not json'
Check ($LASTEXITCODE -eq 0) 'مدخل خربان = ما يطيح'

# ── Codex notify ──
$nj = '{"type":"agent-turn-complete","thread-id":"t1","cwd":"C:\\work\\octopus"}'
# Windows PowerShell 5.1 يشيل علامات التنصيص من معاملات البرامج الخارجية، وCodex نفسه يمررها صح (CreateProcess)
if ($PSVersionTable.PSVersion.Major -lt 7) { $nj = $nj -replace '"', '\"' }
& $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'codex-notify.ps1') $nj
$c = Ev 'codex-t1'
Check ($c.state -eq 'ready' -and $c.tool -eq 'codex' -and $c.project -eq 'octopus') 'Codex notify = جاهزة'

# ── التركيب: يحافظ على الموجود، ومرتين ما يكرر ──
$claudeDir = Join-Path $env:USERPROFILE '.claude'; New-Item -ItemType Directory -Force $claudeDir | Out-Null
[IO.File]::WriteAllText((Join-Path $claudeDir 'settings.json'), '{"theme":"dark","env":{"X":"1"},"hooks":{"Stop":[{"hooks":[{"type":"command","command":"echo hi"}]}]}}')
$codexDir = Join-Path $env:USERPROFILE '.codex'; New-Item -ItemType Directory -Force $codexDir | Out-Null
[IO.File]::WriteAllText((Join-Path $codexDir 'config.toml'), "model = `"gpt-5`"`r`n[projects.x]`r`ntrust_level = `"trusted`"`r`n")
$env:SIRFARI_SOURCE = $here
$env:SIRFARI_NO_START = '1'
& $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'install.ps1')
& $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'install.ps1')
$s = [IO.File]::ReadAllText((Join-Path $claudeDir 'settings.json')) | ConvertFrom-Json
Check ($s.theme -eq 'dark' -and $s.env.X -eq '1') 'الإعدادات الموجودة باقية'
Check (@($s.hooks.Stop).Count -eq 2) 'Stop: hook أمجد باقي + hook سيرفري مرة وحدة'
foreach ($ev in 'SessionStart', 'UserPromptSubmit', 'PostToolUse', 'PermissionRequest', 'Notification', 'SessionEnd') { Check (@($s.hooks.$ev).Count -eq 1) "hook $ev مرة وحدة" }
Check (@($s.hooks.PostToolUse)[0].matcher -eq '*') 'PostToolUse matcher'
Check ([string]@(@($s.hooks.Stop)[1].hooks)[0].command -like '*Sirfari*hook.ps1*') 'أمر الـ hook'
$toml = [IO.File]::ReadAllText((Join-Path $codexDir 'config.toml'))
Check ($toml -match '^notify = \[' -and ([regex]::Matches($toml, 'notify =')).Count -eq 1) 'Codex notify في أول الملف ومرة وحدة'
Check ($toml -match '\[projects\.x\]') 'إعداد Codex الموجود باقي'
Check (@(Get-ChildItem $claudeDir -Filter 'settings.json.bak-sirfari-*').Count -ge 1) 'نسخة احتياطية'

# ── التقرير ──
$out = Join-Path $root 'report.json'
& $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'agent.ps1') -DryRun $out
$r = [IO.File]::ReadAllText($out) | ConvertFrom-Json
Check ($r.device.id -match '^[0-9a-f]{32}$' -and $r.device.os -eq 'windows') 'هوية الجهاز'
Check (@($r.sessions).Count -eq 2) 'التقرير فيه الجلستين'
Check ((@($r.sessions) | Where-Object { $_.sessionId -eq 'abc-1' }).state -eq 'needs') 'حالة الجلسة في التقرير'

if ($fail -gt 0) { Write-Host "$fail failed"; exit 1 }
Write-Host 'all passed'
