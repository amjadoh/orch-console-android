package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// كل جلسات Claude وCodex اللي تاخذ من رام السيرفر، من أي مكان انفتحت. الحالة من hooks حق Claude Code (صادقة):
// تشتغل / تحتاجك / جاهزة / غير معروف. القفل بالبصمة لجلسة Claude بعينها، بس لو جاهزة من 10 دقايق
// (أو غير معروفة مع تأكيدك). Codex: كل محادثة لحالها (الحالة من خدمة Codex نفسها)، وتنقفل بالبصمة بنفس القاعدة
// (القفل = أرشفة: تبقى في «المؤرشفة» في تطبيق Codex). خدمة Codex نفسها تطلع برامها بس.

data class SessionInfo(val id: String, val pid: Int, val start: Long, val kind: String, val label: String, val project: String?, val ageSec: Long?, val rssMb: Long?, val state: String, val stateSec: Long?, val subagents: Int = 0) {
    val isCodex get() = kind.startsWith("codex")
    /** محادثة Codex بعينها (id = رقم المحادثة) */
    val isCodexThread get() = kind == "codex-thread"
    val unknown get() = state == "unknown"
    /** ينفع يتقفل؟ null = إيه، وإلا السبب */
    val blockReason: String? get() = when {
        kind == "codex-app" -> "هذي خدمة Codex نفسها وفيها كل المحادثات. اقفل المحادثات وحدة وحدة"
        kind == "codex-cli" -> "Codex في الطرفية يتقفل من الطرفية نفسها"
        isCodexThread && unknown -> "حالتها غير معروفة، اقفلها من تطبيق Codex"
        state == "working" -> "تشتغل الحين"
        state == "needs" -> "تنتظر موافقتك على شي"
        state == "ready" && (stateSec ?: 0) < 600 -> "خلصت ردها قبل شوي، يمكن ترجع لها"
        else -> null
    }
}
data class DeviceSession(val sessionId: String, val tool: String, val project: String?, val state: String, val stateSec: Long?)
data class DeviceInfo(val id: String, val name: String, val stale: Boolean, val sessions: List<DeviceSession>, val ramMb: Long)
data class SessionsView(val all: List<SessionInfo>, val stale: Boolean, val openable: List<String>, val installed: Boolean, val devices: List<DeviceInfo> = emptyList(), val openNow: List<String> = emptyList(), val codexDown: Boolean = false, val codexPartial: Boolean = false) {
    /** كل المشاريع (Codex يقدر يفتح حتى اللي فيه جلسة Claude مفتوحة) */
    val allProjects get() = (openable + openNow).distinct().sorted()
}

/** اسم مشروع جديد: نفس شروط السيرفر (حروف وأرقام ومسافة و- و_ و.، 40 حرف) */
fun validNewProject(name: String, existing: List<String>): String? {
    val n = name.trim()
    return when {
        n.isEmpty() -> "اكتب اسم المشروع"
        n.length > 40 -> "الاسم طويل (40 حرف بالكثير)"
        !Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]*$").matches(n) || n.contains("..") || n.contains(".bak", ignoreCase = true) || Regex("-[0-9a-f]{7}$").containsMatchIn(n) -> "حروف وأرقام ومسافة و- و_ بس"
        existing.any { it.equals(n, ignoreCase = true) } -> "فيه مشروع بهالاسم، اختره من القائمة"
        else -> null
    }
}

object SessionsModel {
    fun parse(o: JSONObject): SessionsView {
        val a = o.optJSONObject("all")
        val arr = a?.optJSONArray("sessions")
        val all = (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it) }.map {
            SessionInfo(it.optString("id"), it.optInt("pid"), it.optLong("start"), it.optString("kind"), it.optString("label"),
                it.optString("project").takeIf { p -> p.isNotBlank() && p != "null" },
                if (it.isNull("ageSec")) null else it.optLong("ageSec"), if (it.isNull("rssMb")) null else it.optLong("rssMb"),
                it.optString("state", "unknown"), if (it.isNull("stateSec")) null else it.optLong("stateSec"))
        }
        // «📂 تقدر تفتح: a، b، c» من قائمة rc-ctl
        val line = o.optString("list").lines().firstOrNull { it.contains("تقدر تفتح") }
        val openable = line?.substringAfter(":", "")?.split("،")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        // «• المشروع — آخر استخدام…» تحت «المفتوح»
        val openNow = o.optString("list").lines().filter { it.startsWith("• ") }.map { it.removePrefix("• ").substringBefore(" — ").trim() }.filter { it.isNotBlank() }
        val devs = o.optJSONArray("devices")
        val devices = (0 until (devs?.length() ?: 0)).mapNotNull { devs!!.optJSONObject(it) }.map { d ->
            val ss = d.optJSONArray("sessions")
            val ps = d.optJSONArray("processes")
            DeviceInfo(
                d.optString("id"), d.optString("name"), d.optBoolean("stale"),
                (0 until (ss?.length() ?: 0)).mapNotNull { ss!!.optJSONObject(it) }.map { x ->
                    DeviceSession(x.optString("sessionId"), x.optString("tool"), x.optString("project").takeIf { p -> p.isNotBlank() && p != "null" }, x.optString("state", "unknown"), if (x.isNull("stateSec")) null else x.optLong("stateSec"))
                },
                (0 until (ps?.length() ?: 0)).sumOf { ps!!.optJSONObject(it)?.optLong("rssMb") ?: 0L },
            )
        }
        // محادثات Codex (من خدمة Codex عن طريق orch-sessions)
        val cx = o.optJSONObject("codex")
        val ct = cx?.optJSONArray("threads")
        val threads = (0 until (ct?.length() ?: 0)).mapNotNull { ct!!.optJSONObject(it) }.map {
            val name = it.optString("name").takeIf { n -> n.isNotBlank() && n != "null" }
            SessionInfo(it.optString("id"), 0, 0, "codex-thread", "محادثة Codex",
                name ?: it.optString("project").takeIf { p -> p.isNotBlank() && p != "null" }, null, null,
                it.optString("state", "unknown"), if (it.isNull("stateSec")) null else it.optLong("stateSec"), it.optInt("subagents"))
        }
        return SessionsView(threads + all, a?.optBoolean("stale") ?: true, openable, o.optBoolean("installed"), devices, openNow, codexDown = cx != null && !cx.optBoolean("ok", true), codexPartial = cx != null && !cx.optBoolean("complete", true))
    }
}

fun dur(sec: Long?): String = when {
    sec == null -> "—"
    sec < 90 -> "دقيقة"
    sec < 3600 -> "${sec / 60} د"
    sec < 172_800 -> "${sec / 3600} س"
    else -> "${sec / 86400} يوم"
}

/** قسم «الجلسات» في السيرفر */
@Composable
fun SessionsSection(v: SessionsView?, actions: Actions, onChanged: () -> Unit) {
    if (v == null) return
    var stop by remember { mutableStateOf<SessionInfo?>(null) }
    var open by remember { mutableStateOf(false) }
    val total = v.all.sumOf { it.rssMb ?: 0 }
    Section("الجلسات المفتوحة", "+ افتح جلسة", { open = true }) {
        Panel(padding = 0) {
            if (v.all.isEmpty()) ListRow(divider = false) { Text("ما فيه جلسات Claude أو Codex شغّالة", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            v.all.forEachIndexed { i, s ->
                ListRow(Modifier.clickable { stop = s }, divider = i < v.all.lastIndex) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val c = if (s.isCodex) Tone.action else Color(0xFFE7A27A)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(if (s.isCodex) "X" else "C", style = MaterialTheme.typography.labelLarge, color = c)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.project ?: s.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (s.isCodexThread) "${s.label}${if (s.subagents > 0) " · معها ${s.subagents} وكلاء" else ""}"
                                else "${s.label} · صار لها ${dur(s.ageSec)} · ${s.rssMb ?: "—"} ميجا",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                        StateChip(s)
                    }
                }
            }
        }
        if (v.all.isNotEmpty()) Text("المجموع ${total} ميجا من رام السيرفر${if (v.stale) " · القائمة قديمة شوي" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (v.codexDown) Text("خدمة Codex ما ردت، فمحادثات Codex ما تطلع الحين", style = MaterialTheme.typography.labelSmall, color = Tone.warn)
        else if (v.codexPartial) Text("محادثات Codex كثيرة، وظاهر منها جزء بس", style = MaterialTheme.typography.labelSmall, color = Tone.warn)
    }
    v.devices.forEach { d -> DeviceSection(d) }
    stop?.let { s -> StopSheet(s, actions, onClose = { stop = null }, onDone = onChanged) }
    if (open) OpenSheet(v.openable, v.allProjects, actions, onClose = { open = false }, onDone = onChanged)
}

/** جلسات اللابتوب (من sirfari-agent): للعرض، والتحكم من اللابتوب نفسه */
@Composable
fun DeviceSection(d: DeviceInfo) {
    Section("اللابتوب · ${d.name}", if (d.stale) "انقطع" else "متصل") {
        Panel(padding = 0) {
            if (d.sessions.isEmpty()) ListRow(divider = false) { Text("ما فيه جلسات Claude أو Codex مفتوحة", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            d.sessions.forEachIndexed { i, s ->
                ListRow(divider = i < d.sessions.lastIndex) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val codex = s.tool == "codex"
                        val c = if (codex) Tone.action else Color(0xFFE7A27A)
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(if (codex) "X" else "C", style = MaterialTheme.typography.labelLarge, color = c)
                        }
                        Text(s.project ?: if (codex) "Codex" else "Claude", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (codex) Chip("آخر رد ${dur(s.stateSec)}", Tone.idle) else StateChipOf(s.state, s.stateSec)
                    }
                }
            }
        }
        if (d.ramMb > 0) Text("Claude وCodex ياخذون ${d.ramMb} ميجا من رام اللابتوب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StateChipOf(state: String, sec: Long?) = when (state) {
    "working" -> Chip("تشتغل", Tone.run)
    "needs" -> Chip("تحتاجك", Tone.warn)
    "ready" -> Chip("جاهزة · ${dur(sec)}", Tone.ok)
    else -> Chip("غير معروف", Tone.idle)
}

@Composable
fun StateChip(s: SessionInfo) = when (s.state) {
    "working" -> Chip("تشتغل", Tone.run)
    "needs" -> Chip("تحتاجك", Tone.warn)
    "ready" -> Chip("جاهزة · ${dur(s.stateSec)}", Tone.ok)
    else -> Chip("غير معروف", Tone.idle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSheet(s: SessionInfo, actions: Actions, onClose: () -> Unit, onDone: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    LaunchedEffect(msg) { if (msg?.first == true) { delay(1800); onDone(); onClose() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Chip(s.label, if (s.isCodex) Tone.action else Tone.warn)
            Text(s.project ?: s.label, style = MaterialTheme.typography.headlineSmall)
            Panel(padding = 16) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateChip(s)
                    Text(if (s.isCodexThread) "آخر نشاط قبل ${dur(s.stateSec)}${if (s.subagents > 0) " · معها ${s.subagents} وكلاء" else ""}" else "صار لها ${dur(s.ageSec)} · تاخذ ${s.rssMb ?: "—"} ميجا", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    when {
                        s.isCodexThread -> "تنشال من خدمة Codex (مع وكلائها)، والمحادثة تبقى في «المؤرشفة» في تطبيق Codex وتقدر ترجعها. وقت القفل يتأكد من جديد إنها ما تشتغل."
                        s.kind == "codex-app" -> "خدمة Codex على السيرفر: فيها كل محادثات Codex، وتطلع في تطبيق ChatGPT. ما تنقفل من هنا، اقفل المحادثات وحدة وحدة."
                        s.isCodex -> "Codex في الطرفية: يتقفل من الطرفية نفسها."
                        s.kind == "claude-rc" -> "جلسة فتحتها من سيرفري: تنقفل نهائياً (ما ترجع تنفتح لحالها)، وتقدر تفتحها مرة ثانية من «افتح جلسة»."
                        else -> "تنقفل بهدوء، والمحادثة محفوظة وتقدر تكمّلها من تطبيق Claude. وقت القفل يتأكد من جديد إنها ما تشتغل."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var sure by remember { mutableStateOf(false) }
            if (s.unknown && !s.isCodex) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { sure = !sure },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Checkbox(checked = sure, onCheckedChange = { sure = it })
                Text("حالتها غير معروفة (انفتحت قبل التحديث). متأكد إنها مو شغّالة", style = MaterialTheme.typography.bodyMedium)
            }
            val reason = s.blockReason
            if (reason == null) FingerprintButton(
                "اقفلها بالبصمة", "انقفلت", listOf("بصمتك", "يتأكد من حالتها", "يقفلها"), enabled = !s.unknown || sure,
                action = { onStep -> onStep(0); if (s.isCodexThread) actions.stopCodex(s) { onStep(1) } else actions.stopSession(s, force = s.unknown && sure) { onStep(1) } },
                onResult = { ok, m -> msg = ok to m },
            ) else Text("ما تنقفل الحين: $reason", color = Tone.warn, style = MaterialTheme.typography.bodyMedium)
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OpenSheet(openable: List<String>, all: List<String>, actions: Actions, onClose: () -> Unit, onDone: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var codex by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    LaunchedEffect(msg) { if (msg?.first == true) { delay(2500); onDone(); onClose() } }
    // Claude: المشاريع اللي ما فيها جلسة مفتوحة. Codex: كلها
    val projects = if (codex) all else openable
    val nameErr = if (creating) validNewProject(newName, all) else null
    val target = if (creating) newName.trim().takeIf { nameErr == null } else pick?.takeIf { it in projects }
    val tool = if (codex) "Codex" else "Claude"
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("افتح جلسة", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Claude", true to "Codex").forEach { (c, label) -> Pill(label, codex == c) { codex = c; msg = null } }
            }
            Text(
                if (codex) "تطلع لك في تطبيق ChatGPT (Codex) على السيرفر. تنقفل من تطبيق Codex."
                else "تطلع لك في تطبيق Claude (Code) على الجوال واللابتوب. الحد 3 جلسات، وتنقفل لحالها لو ما استخدمتها.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (projects.isEmpty()) Text("القائمة ما وصلت. اسحب للتحديث وجرّب.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { p -> Pill(p, !creating && p == pick) { pick = p; creating = false; msg = null } }
                Pill("+ مشروع جديد", creating) { creating = true; msg = null }
            }
            if (creating) OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(40); msg = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم المشروع الجديد") }, placeholder = { Text("مثلاً: متجر-العطور") },
                isError = newName.isNotBlank() && nameErr != null,
                supportingText = { Text(if (newName.isNotBlank() && nameErr != null) nameErr else "يتسوى مجلده في ~/work/projects وفيه git وملف تسليم") },
            )
            FingerprintButton(
                target?.let { if (creating) "سوّ مشروع $it وافتح $tool بالبصمة" else "افتح $tool على $it بالبصمة" } ?: if (creating) "اكتب اسم المشروع" else "اختر مشروع",
                "انفتحت", listOf("بصمتك", if (creating) "يتسوى المشروع وتنفتح الجلسة" else "تنفتح الجلسة"), enabled = target != null,
                action = { onStep -> onStep(0); actions.openSession(target!!, codex = codex, create = creating).also { onStep(1) } },
                onResult = { ok, m -> msg = ok to m },
            )
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}
