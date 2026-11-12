package com.amjadoh.sirfari

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val STATUS = mapOf(
    "awaiting" to ("تنتظرك" to Tone.warn), "running" to ("شغالة" to Tone.run), "done" to ("خلصت" to Tone.ok),
    "failed" to ("فشلت" to Tone.bad), "cancelled" to ("انلغت" to Tone.idle), "new" to ("جديدة" to Tone.idle),
)
fun statusOf(s: String) = STATUS[s] ?: (s to Tone.idle)

/** المهام: القائمة، ولما ينفتح الجوال (أو شاشة عريضة) القائمة والمهمة جنب بعض */
@Composable
fun TasksScreen(
    r: Repo.Result<Overview>?, admin: JSONObject?, now: Long, selected: String?, onSelect: (String?) -> Unit,
    api: Api, actions: Actions, onCommand: () -> Unit, onChanged: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 700.dp
        if (wide && selected != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(12.dp)) { TaskList(r, admin, now, selected, onSelect, actions, onCommand, onChanged) }
                Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(12.dp)) { key(selected) { TaskDetailPane(selected, api, actions, now, onSelect, onChanged) } }
            }
        } else if (selected != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { onSelect(null) }) { Text("→ كل المهام") }
                key(selected) { TaskDetailPane(selected, api, actions, now, onSelect, onChanged) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { TaskList(r, admin, now, null, onSelect, actions, onCommand, onChanged) }
        }
    }
}

@Composable
internal fun TaskList(
    r: Repo.Result<Overview>?, admin: JSONObject?, now: Long, selected: String?, onSelect: (String?) -> Unit,
    actions: Actions, onCommand: () -> Unit, onChanged: () -> Unit,
) {
    FreshnessBar(r, now)
    Button(onClick = onCommand, modifier = Modifier.fillMaxWidth()) { Text("+ أمر جديد") }
    AdminRequests(admin, actions, onChanged)
    val tasks = r?.data?.tasks ?: return
    if (tasks.isEmpty()) { Panel { Text("ما فيه مهام للحين") }; return }
    val groups = listOf(
        "تنتظر موافقتك" to tasks.filter { it.status == "awaiting" },
        "شغالة" to tasks.filter { it.status == "running" || it.status == "new" },
        "آخر المهام" to tasks.filter { it.status !in setOf("awaiting", "running", "new") }.take(20),
    )
    for ((title, list) in groups) if (list.isNotEmpty()) Panel(title) {
        list.forEach { t ->
            val (label, color) = statusOf(t.status)
            Column(Modifier.fillMaxWidth().clickable { onSelect(t.taskId) }.padding(vertical = 4.dp)) {
                Text(t.title.ifBlank { t.taskId }, fontWeight = if (t.taskId == selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                StatusLine(color, "${t.taskId} · ${t.projectId}", label)
            }
        }
    }
}

// ── طلبات root (التثبيت والحلول الجاهزة) ──
@Composable
private fun AdminRequests(admin: JSONObject?, actions: Actions, onChanged: () -> Unit) {
    val list = admin?.optJSONArray("requests") ?: return
    val pending = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.filter { it.optString("state") == "pending" }
    if (pending.isEmpty()) return
    val scope = rememberCoroutineScope()
    Panel("طلبات إدارية (root)") {
        pending.forEach { req ->
            var busy by remember(req.optString("id")) { mutableStateOf(false) }
            var msg by remember(req.optString("id")) { mutableStateOf<Pair<Boolean, String>?>(null) }
            Text(req.optString("summary"), style = MaterialTheme.typography.bodyMedium)
            Text("${req.optString("id")} · ${req.optString("action")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        msg = runCatching { actions.adminApprove(req) }.fold({ true to it }, { false to (it.message ?: "صار خطأ") })
                        busy = false; onChanged()
                    }
                }) { Text("وافق بالبصمة") }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        msg = runCatching { actions.adminReject(req.optString("id")); "انرفض الطلب" }.fold({ true to it }, { false to (it.message ?: "صار خطأ") })
                        busy = false; onChanged()
                    }
                }) { Text("ارفض") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error) }
        }
    }
}

// ── صفحة المهمة ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDetailPane(taskId: String, api: Api, actions: Actions, now: Long, onSelect: (String?) -> Unit, onChanged: () -> Unit) {
    var t by remember { mutableStateOf<TaskDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var hold by remember { mutableStateOf(false) } // وانت تكتب أو تنتظر البصمة: ما نحدّث تحتك
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(taskId, reload) {
        while (true) {
            if (!hold) runCatching { Models.task(api.get("/api/tasks/$taskId")) }.fold({ t = it; err = null }, { err = it.message })
            delay(4000)
        }
    }

    err?.let { Panel { Text(it, color = MaterialTheme.colorScheme.error) } }
    val d = t ?: return
    TaskDetailBody(d, actions, onSelect, onHold = { hold = it }, onDone = { reload++; onChanged() })
}

/** محتوى صفحة المهمة (منفصل عن التحميل عشان ينختبر ويتصوّر) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskDetailBody(d: TaskDetail, actions: Actions, onSelect: (String?) -> Unit, onHold: (Boolean) -> Unit, onDone: () -> Unit) {
    val (label, color) = statusOf(d.status)
    val uri = LocalUriHandler.current

    Panel {
        Text(d.title.ifBlank { d.taskId }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        StatusLine(color, "${d.taskId} · ${d.projectId}", label)
        val who = listOfNotNull(d.first?.let { "$it ينفذ" }, d.second?.let { "$it يراجع" }).joinToString(" · ")
        if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.costUsd?.let { Text("التكلفة: $" + "%.2f".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.issueUrl?.let { u -> TextButton(onClick = { uri.openUri(u) }) { Text("الطلب في GitHub") } }
            d.branchUrl?.let { u -> TextButton(onClick = { uri.openUri(u) }) { Text("الفرع") } }
            d.attempts?.next?.let { n -> TextButton(onClick = { onSelect(n) }) { Text("المحاولة اللي بعدها: $n") } }
        }
    }

    Panel("الطلب") { Text(d.request, style = MaterialTheme.typography.bodyMedium) }

    // الخطة
    when {
        d.plan != null -> Panel(if (d.plan.planner != null) "الخطة (كتبها ${d.plan.planner})" else "الخطة") {
            Text(d.plan.summary)
            if (d.plan.steps.isNotEmpty()) d.plan.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
            if (d.plan.files.isNotEmpty()) {
                Text("الملفات", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                d.plan.files.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
            }
            if (d.plan.risks.isNotEmpty()) {
                Text("المخاطر", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                d.plan.risks.forEach { Text("⚠️ $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (d.waitingPlan) Text("المنفذ يعدّل الخطة…", color = Tone.run)
        }
        d.waitingPlan -> Panel { Text("المنفذ يجهّز الخطة…", color = Tone.run) }
        d.planState == "failed" -> Panel { Text("التخطيط فشل: ${d.planFailReason ?: "بدون سبب"}", color = Tone.bad) }
    }

    // المتابعة الحية
    d.activity?.takeIf { it.items.isNotEmpty() }?.let { a ->
        Panel(if (a.live) "🔴 مباشر: ${a.agent ?: "الوكيل"} يشتغل" else "آخر حركات ${a.agent ?: "الوكيل"}") {
            a.items.takeLast(12).reversed().forEach { x ->
                val icon = when (x.kind) { "read" -> "📖"; "edit" -> "✏️"; "run" -> "▶️"; "search" -> "🔎"; else -> "•" }
                val what = x.path ?: x.cmd ?: x.tool
                Text("$icon $what", style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }

    d.summary?.let { s -> Panel("الخلاصة") { Text(s) } }

    Decisions(d, actions, onHold = onHold, onDone = onDone)

    if (d.status == "awaiting" || d.status == "running") ContinueYourself(d)
}

@Composable
private fun ContinueYourself(d: TaskDetail) {
    val branch = d.branchUrl ?: return
    Panel("كمّل عليها بنفسك") {
        Text("انسخ هالجملة لجلسة Claude عندك:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("كمّل على المهمة ${d.taskId} من فرعها: $branch", style = MaterialTheme.typography.bodyMedium)
    }
}

// ── القرارات بالبصمة ──
@Composable
private fun Decisions(t: TaskDetail, actions: Actions, onHold: (Boolean) -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(-1) }
    var purpose by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var revising by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    fun go(p: String, n: String? = null) {
        if (busy) return
        busy = true; purpose = p; msg = null; step = 0; onHold(true)
        scope.launch {
            msg = runCatching { actions.decide(t.taskId, p, n) { step = it } }.fold({ true to it }, { false to (it.message ?: "صار خطأ") })
            busy = false; onHold(false); revising = false; note = ""
            onDone()
        }
    }

    val buttons: List<Triple<String, String, Boolean>> = when (t.status) {
        "awaiting" -> buildList {
            if (t.planReady) add(Triple("approve", "وافق بالبصمة", true))
            if (t.canRevise) add(Triple("revise", "✏️ عدّل الخطة (باقي ${t.revisionsLeft})", false))
            if (t.canSwap) add(Triple("switch", "🔄 خلّ ${t.second} ينفذ", false))
            if (t.planState == "failed") add(Triple("replan", "🔁 أعد التخطيط", true))
            add(Triple("cancel", "ألغِ المهمة", false))
        }
        "running" -> listOf(Triple("stop", "⏹️ أوقف المهمة", false))
        "failed", "cancelled" -> if (t.canRetry) listOf(Triple("retry", "🔁 أعد المحاولة", true)) else emptyList()
        else -> emptyList()
    }
    if (buttons.isEmpty() && msg == null) return

    Panel("قرارك") {
        if (t.status == "awaiting" && t.waitingPlan) Text("انتظر الخطة تجهز قبل الموافقة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRowButtons(buttons, busy) { p ->
            when (p) {
                "revise" -> { revising = !revising; onHold(revising) }
                "cancel", "switch", "stop" -> confirm = p
                else -> go(p)
            }
        }
        if (revising) {
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(300) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                label = { Text("وش تبغى يتغير في الخطة؟") }, supportingText = { Text("${note.length}/300 · يعيد الخطة بدون ما يعدّل أي ملف") },
            )
            Button(enabled = !busy && note.trim().length >= 3, onClick = { go("revise", note.trim()) }) { Text("أرسل التعديل بالبصمة") }
        }
        if (busy && purpose != null) {
            val labels = Actions.STEPS[purpose] ?: listOf("بصمتك", "التنفيذ")
            labels.forEachIndexed { i, l -> Text((if (i < step || step == 99) "✓ " else if (i == step) "… " else "  ") + l, color = if (i <= step || step == 99) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error) }
    }

    confirm?.let { p ->
        val text = when (p) {
            "cancel" -> "تلغي ${t.taskId}؟"
            "switch" -> "${t.second} ينفذ و${t.first} يراجع؟ الخطة تبقى نفسها."
            else -> "توقف ${t.taskId}؟ المنفذ يوقف، والشغل الناقص يبقى على السيرفر وما ينحذف ولا ينرفع."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; go(p) }) { Text("إيه، بالبصمة") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("لا") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(buttons: List<Triple<String, String, Boolean>>, busy: Boolean, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (p, label, primary) ->
            if (primary) Button(onClick = { onClick(p) }, enabled = !busy) { Text(label) }
            else OutlinedButton(onClick = { onClick(p) }, enabled = !busy,
                colors = if (p == "cancel" || p == "stop") ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
            ) { Text(label) }
        }
    }
}
