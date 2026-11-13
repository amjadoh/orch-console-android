package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val STATUS = mapOf(
    "awaiting" to ("تنتظرك" to Tone.warn), "running" to ("شغّالة" to Tone.run), "done" to ("خلصت" to Tone.ok),
    "failed" to ("فشلت" to Tone.bad), "cancelled" to ("انلغت" to Tone.idle), "new" to ("جديدة" to Tone.idle),
)
fun statusOf(s: String) = STATUS[s] ?: (s to Tone.idle)

private enum class Filter(val label: String) { All("الكل"), Awaiting("تنتظرك"), Running("شغّالة"), Done("خلصت"), Failed("فشلت") }

/**
 * المهام: والجوال مقفول قائمة أو مهمة. ولما ينفتح (≥700dp) القائمة يمين والمهمة يسار، وكل عمود يتمرر لحاله.
 * [bottomInset] مساحة شريط التبويبات العائم تحت.
 */
@Composable
fun TasksScreen(
    r: Repo.Result<Overview>?, admin: JSONObject?, now: Long, selected: String?, onSelect: (String?) -> Unit,
    api: Api, actions: Actions, onChanged: () -> Unit, bottomInset: Int = 96,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        if (wide) {
            val tasks = r?.data?.tasks.orEmpty()
            LaunchedEffect(selected, tasks.size) {
                if (selected == null) (tasks.firstOrNull { it.status == "awaiting" } ?: tasks.firstOrNull { it.status == "running" } ?: tasks.firstOrNull())?.let { onSelect(it.taskId) }
            }
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 16.dp, bottom = bottomInset.dp + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) { TaskList(r, admin, now, selected, onSelect, actions, onChanged) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(LocalSirfari.current.line))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) key(selected) { TaskDetailPane(selected, api, actions, onSelect, onChanged, showBack = false, bottomInset = bottomInset) }
                    else Text("اختر مهمة", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (selected != null) {
            key(selected) { TaskDetailPane(selected, api, actions, onSelect, onChanged, showBack = true, bottomInset = bottomInset) }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomInset.dp + 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) { TaskList(r, admin, now, null, onSelect, actions, onChanged) }
        }
    }
}

@Composable
internal fun TaskList(
    r: Repo.Result<Overview>?, admin: JSONObject?, now: Long, selected: String?, onSelect: (String?) -> Unit,
    actions: Actions, onChanged: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var adminReq by remember { mutableStateOf<JSONObject?>(null) }
    var projects by rememberSaveable { mutableStateOf(false) }

    FreshnessBar(r, now)
    OutlinedTextField(
        value = query, onValueChange = { query = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("ابحث بمهمة أو مشروع") }, shape = RoundedCornerShape(16.dp),
        leadingIcon = { Icon(SirfariIcons.Search, null, Modifier.size(20.dp)) },
    )
    val all = r?.data?.tasks.orEmpty()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filter.entries.forEach { f ->
            val n = when (f) { Filter.Awaiting -> all.count { it.status == "awaiting" }; Filter.Running -> all.count { it.status == "running" || it.status == "new" }; else -> 0 }
            val sel = f == filter
            Text(
                if (n > 0) "${f.label} $n" else f.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal),
                color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50)).then(
                    if (sel) Modifier.background(MaterialTheme.colorScheme.onSurface) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                ).clickable { filter = f }.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }

    val reqs = pendingAdmin(admin)
    if (reqs.isNotEmpty() && (filter == Filter.All || filter == Filter.Awaiting)) Section("طلبات root") {
        reqs.forEach { req ->
            Panel(padding = 14, modifier = Modifier.clickable { adminReq = req }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SirfariIcons.Shield, null, tint = Tone.run, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(req.optString("summary").ifBlank { req.optString("action") }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("يحتاج بصمتك", style = MaterialTheme.typography.labelMedium, color = Tone.warn)
                    }
                }
            }
        }
    }

    if (r?.data == null) { if (r?.offline != true) LoadState(null) {}; return }
    val q = query.trim().lowercase()
    val list = all.filter { t ->
        (q.isEmpty() || t.title.lowercase().contains(q) || t.taskId.lowercase().contains(q) || t.projectId.lowercase().contains(q)) &&
            when (filter) {
                Filter.All -> true; Filter.Awaiting -> t.status == "awaiting"; Filter.Running -> t.status == "running" || t.status == "new"
                Filter.Done -> t.status == "done"; Filter.Failed -> t.status == "failed" || t.status == "cancelled"
            }
    }.sortedBy { when (it.status) { "awaiting" -> 0; "running", "new" -> 1; else -> 2 } }
    if (list.isEmpty()) Panel { Text(if (q.isEmpty()) "ما فيه مهام هنا" else "ما لقيت شي بـ «$query»", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.take(40).forEach { t -> TaskRow(t, t.taskId == selected, now) { onSelect(t.taskId) } }
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { projects = !projects }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("مشاريعي", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(SirfariIcons.ChevronDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (projects) ProjectsSection(actions.api) { onSelect(it) }

    adminReq?.let { req -> AdminSheet(req, actions, onClose = { adminReq = null }, onDone = onChanged) }
}

@Composable
private fun TaskRow(t: TaskCard, selected: Boolean, now: Long, onClick: () -> Unit) {
    val (label, color) = statusOf(t.status)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else LocalSirfari.current.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(t.title.ifBlank { t.taskId }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label + (t.since?.takeIf { t.status == "running" || t.status == "awaiting" }?.let { " · ${ago(it, now)}" } ?: ""), style = MaterialTheme.typography.labelMedium, color = color)
            Mono("${t.projectId} · ${t.taskId}", Modifier.weight(1f, fill = false))
        }
    }
}

// ── صفحة المهمة ──
@Composable
private fun TaskDetailPane(taskId: String, api: Api, actions: Actions, onSelect: (String?) -> Unit, onChanged: () -> Unit, showBack: Boolean, bottomInset: Int) {
    var t by remember { mutableStateOf<TaskDetail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var hold by remember { mutableStateOf(false) } // وانت تكتب أو تبصم: ما نحدّث تحتك
    var reload by remember { mutableIntStateOf(0) }
    val tick = LocalRefresh.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // تتحدث كل 4 ثواني بس والتطبيق قدامك والمهمة لسا ماشية (تنتظرك أو شغّالة). المنتهية تنقرا مرة وحدة.
    LaunchedEffect(taskId, reload, tick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (!hold) runCatching { Models.task(api.get("/api/tasks/$taskId")) }.fold({ t = it; err = null }, { err = it.message })
                if (t != null && t!!.status !in setOf("awaiting", "running", "new")) break
                delay(4000)
            }
        }
    }

    val d = t
    if (d == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (showBack) SquareIcon(SirfariIcons.Back, "رجوع") { onSelect(null) }
            LoadState(err) { reload++ }
        }
        return
    }
    TaskPage(d, actions, onSelect, onHold = { hold = it }, onDone = { reload++; onChanged() }, showBack = showBack, bottomInset = bottomInset)
}

/** صفحة المهمة كاملة: المحتوى يتمرر، وشريط القرار ثابت تحت */
@Composable
internal fun TaskPage(d: TaskDetail, actions: Actions, onSelect: (String?) -> Unit, onHold: (Boolean) -> Unit, onDone: () -> Unit, showBack: Boolean = false, bottomInset: Int = 0) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { TaskBody(d, onSelect, showBack) }
        DecisionBar(d, actions, onHold, onDone, bottomInset)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskBody(d: TaskDetail, onSelect: (String?) -> Unit, showBack: Boolean = false) {
    val (label, color) = statusOf(d.status)
    val uri = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showBack) SquareIcon(SirfariIcons.Back, "كل المهام") { onSelect(null) }
        Mono("${d.projectId} · ${d.taskId}", Modifier.weight(1f))
        Chip(if (d.status == "awaiting" && d.planReady) "الخطة جاهزة · تنتظرك" else label, color)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(d.title.ifBlank { d.taskId }, style = MaterialTheme.typography.headlineSmall)
        if (d.request.isNotBlank() && d.request.trim() != d.title.trim()) Text(d.request, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    if (d.first != null || d.second != null) Panel(padding = 6) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            d.first?.let { Agent(it, "ينفذ", Tone.warn, Modifier.weight(1f)) }
            if (d.first != null && d.second != null) Text("⇄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.second?.let { Agent(it, "يراجع", Tone.action, Modifier.weight(1f)) }
        }
    }

    when {
        d.plan != null -> Section(if (d.plan.planner != null) "الخطة · كتبها ${d.plan.planner}" else "الخطة") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.plan.summary.isNotBlank()) Text(d.plan.summary, style = MaterialTheme.typography.bodyLarge)
                d.plan.steps.forEachIndexed { i, s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(s, Modifier.weight(1f).padding(top = 1.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (d.plan.files.isNotEmpty() || d.plan.risks.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.plan.files.take(6).forEach { f ->
                        Mono(f.substringAfterLast('/').let { if (f.contains('/')) "…/$it" else it }, Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalSirfari.current.line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    d.plan.risks.forEach { r -> Chip("⚠ $r", Tone.warn) }
                }
                if (d.waitingPlan) Text("المنفذ يعدّل الخطة…", color = Tone.run, style = MaterialTheme.typography.bodySmall)
            }
        }
        d.waitingPlan -> Panel { Text("المنفذ يجهّز الخطة…", color = Tone.run) }
        d.planState == "failed" -> Panel { Text("التخطيط فشل: ${d.planFailReason ?: "بدون سبب"}", color = Tone.bad) }
    }

    // آخر الحركات: «مباشر» بس لما المهمة شغّالة فعلاً. وهي تنتظرك = حركات التخطيط.
    d.activity?.takeIf { it.items.isNotEmpty() }?.let { a ->
        val who = a.agent ?: "الوكيل"
        val live = a.live && d.status == "running"
        val title = when { live -> "مباشر من $who"; d.status == "awaiting" -> "آخر حركات $who وهو يخطط"; else -> "آخر حركات $who" }
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(LocalSirfari.current.live).border(1.dp, Tone.run.copy(alpha = if (live) 0.3f else 0.12f), shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(if (live) Tone.run else Tone.idle, 7, halo = live)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            a.items.takeLast(4).reversed().forEach { x ->
                val (verb, c) = when (x.kind) { "read" -> "read" to Tone.idle; "edit" -> "edit" to Tone.ok; "run" -> "run " to Tone.run; "search" -> "find" to Tone.idle; else -> "    " to Tone.idle }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mono(verb, color = c)
                    Mono(x.path ?: x.cmd ?: x.tool, Modifier.weight(1f))
                }
            }
        }
    }

    d.summary?.let { s -> Section("الخلاصة") { Panel { Text(s) } } }
    if (d.status == "awaiting" || d.status == "running") ContinueYourself(d)

    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        d.issueUrl?.let { u -> TextButton(onClick = { uri.openUri(u) }) { Text("الطلب في GitHub") } }
        d.branchUrl?.let { u -> TextButton(onClick = { uri.openUri(u) }) { Text("الفرع") } }
        d.attempts?.next?.let { n -> TextButton(onClick = { onSelect(n) }) { Text("المحاولة اللي بعدها: $n") } }
        d.costUsd?.let { Text("التكلفة \$" + "%.2f".format(it), Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Agent(name: String, role: String, color: Color, modifier: Modifier) {
    Row(modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
        }
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinueYourself(d: TaskDetail) {
    val branch = d.branchUrl ?: return
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val line = "كمّل على المهمة ${d.taskId} من فرعها: $branch"
    Section("كمّل عليها بنفسك") {
        Panel(padding = 14) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                SquareIcon(SirfariIcons.Copy, if (copied) "انسخ" else "انسخ", tint = if (copied) Tone.ok else MaterialTheme.colorScheme.onSurface) {
                    clip.setText(AnnotatedString(line)); copied = true
                }
            }
            if (copied) Text("انسخت ✓ الصقها لجلسة Claude عندك", style = MaterialTheme.typography.labelSmall, color = Tone.ok)
        }
    }
}

// ── شريط القرار الثابت تحت (بالبصمة) ──
@Composable
private fun DecisionBar(t: TaskDetail, actions: Actions, onHold: (Boolean) -> Unit, onDone: () -> Unit, bottomInset: Int) {
    val scope = LocalAppScope.current ?: rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(-1) }
    var purpose by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var revising by remember { mutableStateOf(false) }
    var note by rememberSaveable(t.taskId) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    // للقرارات من القائمة (إلغاء، تبديل، إيقاف، تعديل): المسودة تبقى لين ينجح الإرسال
    fun go(p: String, n: String? = null) {
        if (busy) return
        busy = true; purpose = p; msg = null; step = 0; onHold(true)
        scope.launch {
            val r = runCatching { actions.decide(t.taskId, p, n) { step = it } }
            busy = false; onHold(false)
            r.onSuccess { msg = true to it; if (p == "revise") { revising = false; note = "" }; onDone() }
                .onFailure { msg = false to (it.message ?: "صار خطأ") }
        }
    }

    data class Main(val purpose: String, val label: String, val done: String, val danger: Boolean = false)
    val main: Main? = when (t.status) {
        "awaiting" -> when {
            t.planReady -> Main("approve", "وافق بالبصمة", "بدأ التنفيذ")
            t.planState == "failed" -> Main("replan", "أعد التخطيط", "بدأ يخطط من جديد")
            else -> null
        }
        "failed", "cancelled" -> if (t.canRetry) Main("retry", "أعد المحاولة", "انفتحت محاولة جديدة") else null
        else -> null
    }
    val more = buildList {
        if (t.status == "awaiting" && t.canSwap) add("switch" to "خلّ ${t.second} ينفذ")
        if (t.status == "awaiting") add("cancel" to "ألغِ المهمة")
        if (t.status == "running") add("stop" to "أوقف المهمة")
    }
    if (main == null && more.isEmpty() && msg == null && !t.waitingPlan) return

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .then(if (bottomInset == 0) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = (bottomInset + 12).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalSirfari.current.line))
        if (t.status == "awaiting" && t.waitingPlan) Text("انتظر الخطة تجهز، وبعدها توافق من هنا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (main != null) FingerprintButton(
                main.label, main.done, Actions.STEPS[main.purpose] ?: emptyList(), Modifier.weight(1f), enabled = !busy, danger = main.danger,
                action = { onStep -> onHold(true); try { actions.decide(t.taskId, main.purpose, null, onStep) } finally { onHold(false) } },
                onResult = { ok, m -> msg = ok to m; if (ok) scope.launch { delay(1300); onDone() } },
            ) else if (t.status == "running") Button(
                onClick = { confirm = "stop" }, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
            ) { Text("أوقف المهمة", style = MaterialTheme.typography.labelLarge) }
            else Box(Modifier.weight(1f))
            val extra = if (t.status == "running") more.filter { it.first != "stop" } else more
            if (extra.isNotEmpty()) Box {
                SquareIcon(SirfariIcons.More, "خيارات أكثر") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    extra.forEach { (p, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (p == "cancel" || p == "stop") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                            onClick = { menu = false; confirm = p },
                        )
                    }
                }
            }
        }
        if (t.canRevise && t.status == "awaiting") TextButton(onClick = { revising = !revising; onHold(revising) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(if (revising) "رجوع" else "عدّل الخطة (باقي ${t.revisionsLeft})", style = MaterialTheme.typography.labelLarge)
        }
        if (revising) {
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(300) }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                label = { Text("وش تبغى يتغير في الخطة؟") }, supportingText = { Text("${note.length}/300 · يعيد الخطة بدون ما يعدّل أي ملف") },
            )
            Button(enabled = !busy && note.trim().length >= 3, onClick = { go("revise", note.trim()) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("أرسل التعديل بالبصمة") }
        }
        if (busy && purpose != null) {
            val labels = Actions.STEPS[purpose] ?: listOf("بصمتك", "التنفيذ")
            Text(labels.getOrElse(step.coerceIn(0, labels.lastIndex)) { "…" }, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
