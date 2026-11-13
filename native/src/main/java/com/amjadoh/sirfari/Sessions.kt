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
// (أو غير معروفة مع تأكيدك). Codex يظهر برامه بس (ما فيه طريقة رسمية تقفله من برّا).

data class SessionInfo(val id: String, val pid: Int, val start: Long, val kind: String, val label: String, val project: String?, val ageSec: Long?, val rssMb: Long?, val state: String, val stateSec: Long?) {
    val isCodex get() = kind.startsWith("codex")
    val unknown get() = state == "unknown"
    /** ينفع يتقفل؟ null = إيه، وإلا السبب */
    val blockReason: String? get() = when {
        isCodex -> "Codex يتقفل من تطبيق Codex"
        state == "working" -> "تشتغل الحين"
        state == "needs" -> "تنتظر موافقتك على شي"
        state == "ready" && (stateSec ?: 0) < 600 -> "خلصت ردها قبل شوي، يمكن ترجع لها"
        else -> null
    }
}
data class DeviceSession(val sessionId: String, val tool: String, val project: String?, val state: String, val stateSec: Long?)
data class DeviceInfo(val id: String, val name: String, val stale: Boolean, val sessions: List<DeviceSession>, val ramMb: Long)
data class SessionsView(val all: List<SessionInfo>, val stale: Boolean, val openable: List<String>, val installed: Boolean, val devices: List<DeviceInfo> = emptyList())

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
        return SessionsView(all, a?.optBoolean("stale") ?: true, openable, o.optBoolean("installed"), devices)
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
                            Text("${s.label} · صار لها ${dur(s.ageSec)} · ${s.rssMb ?: "—"} ميجا", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        StateChip(s)
                    }
                }
            }
        }
        if (v.all.isNotEmpty()) Text("المجموع ${total} ميجا من رام السيرفر${if (v.stale) " · القائمة قديمة شوي" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    v.devices.forEach { d -> DeviceSection(d) }
    stop?.let { s -> StopSheet(s, actions, onClose = { stop = null }, onDone = onChanged) }
    if (open) OpenSheet(v.openable, actions, onClose = { open = false }, onDone = onChanged)
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
                        StateChipOf(s.state, s.stateSec)
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
                    Text("صار لها ${dur(s.ageSec)} · تاخذ ${s.rssMb ?: "—"} ميجا", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    when {
                        s.isCodex -> "Codex ما فيه طريقة رسمية تقفله من برّا. اقفل المحادثة من تطبيق Codex."
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
                action = { onStep -> onStep(0); actions.stopSession(s, force = s.unknown && sure) { onStep(1) } },
                onResult = { ok, m -> msg = ok to m },
            ) else Text("ما تنقفل الحين: $reason", color = Tone.warn, style = MaterialTheme.typography.bodyMedium)
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OpenSheet(projects: List<String>, actions: Actions, onClose: () -> Unit, onDone: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pick by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    LaunchedEffect(msg) { if (msg?.first == true) { delay(2500); onDone(); onClose() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("افتح جلسة Claude", style = MaterialTheme.typography.headlineSmall)
            Text("تطلع لك في تطبيق Claude (Code) على الجوال واللابتوب. الحد 3 جلسات، وتنقفل لحالها لو ما استخدمتها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (projects.isEmpty()) Text("القائمة ما وصلت. اسحب للتحديث وجرّب.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { p ->
                    val sel = p == pick
                    Text(p, style = MaterialTheme.typography.labelLarge, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { pick = p }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            FingerprintButton(
                pick?.let { "افتح جلسة $it بالبصمة" } ?: "اختر مشروع", "انفتحت", listOf("بصمتك", "تنفتح الجلسة"), enabled = pick != null,
                action = { onStep -> onStep(0); actions.openSession(pick!!).also { onStep(1) } },
                onResult = { ok, m -> msg = ok to m },
            )
            Text("Codex ما ينفتح من هنا للحين، افتحه من تطبيق Codex.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
