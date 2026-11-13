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

// كل جلسات Claude وCodex على السيرفر، من أي مكان انفتحت. القفل بالبصمة لجلسة بعينها، وما تنقفل إلا لو نايمة.
// الفتح: جلسة Claude لمشروع (تطلع في تطبيق Claude). Codex ما ينفتح من هنا للحين.

data class SessionInfo(val id: String, val pid: Int, val start: Long, val kind: String, val label: String, val project: String?, val ageSec: Long?, val rssMb: Long?, val idleSec: Long?) {
    val busy get() = idleSec != null && idleSec < 600
    val isCodex get() = kind.startsWith("codex")
}
data class SessionsView(val all: List<SessionInfo>, val stale: Boolean, val openable: List<String>, val installed: Boolean)

object SessionsModel {
    fun parse(o: JSONObject): SessionsView {
        val a = o.optJSONObject("all")
        val arr = a?.optJSONArray("sessions")
        val all = (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it) }.map {
            SessionInfo(it.optString("id"), it.optInt("pid"), it.optLong("start"), it.optString("kind"), it.optString("label"),
                it.optString("project").takeIf { p -> p.isNotBlank() && p != "null" },
                if (it.isNull("ageSec")) null else it.optLong("ageSec"), if (it.isNull("rssMb")) null else it.optLong("rssMb"),
                if (it.isNull("idleSec")) null else it.optLong("idleSec"))
        }
        // «📂 تقدر تفتح: a، b، c» من قائمة rc-ctl
        val line = o.optString("list").lines().firstOrNull { it.contains("تقدر تفتح") }
        val openable = line?.substringAfter(":", "")?.split("،")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return SessionsView(all, a?.optBoolean("stale") ?: true, openable, o.optBoolean("installed"))
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
                        if (s.busy) Chip("شغّالة", Tone.run) else Chip("نايمة ${dur(s.idleSec)}", Tone.idle)
                    }
                }
            }
        }
        if (v.all.isNotEmpty()) Text("المجموع ${total} ميجا رام${if (v.stale) " · القائمة قديمة شوي" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    stop?.let { s -> StopSheet(s, actions, onClose = { stop = null }, onDone = onChanged) }
    if (open) OpenSheet(v.openable, actions, onClose = { open = false }, onDone = onChanged)
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
                Text("صار لها ${dur(s.ageSec)} · تاخذ ${s.rssMb ?: "—"} ميجا · ${if (s.busy) "شغّالة الحين" else "نايمة من ${dur(s.idleSec)}"}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (s.kind == "claude-rc") "جلسة فتحتها من سيرفري: تنقفل على طول، وتقدر تفتحها مرة ثانية من «افتح جلسة»."
                    else "تنقفل بهدوء (مثل ما تسكرها من التطبيق). المحادثة نفسها محفوظة وتقدر تكمّلها من تطبيق ${if (s.isCodex) "Codex" else "Claude"}. لو كانت شغّالة لحظة القفل، ما تنقفل.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.kind == "claude-rc") {
                val scope = LocalAppScope.current ?: rememberCoroutineScope()
                var busy by remember { mutableStateOf(false) }
                androidx.compose.material3.Button(
                    enabled = !busy && s.project != null, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    onClick = { busy = true; scope.launch { msg = runCatching { actions.sessionNoFingerprint("close", s.project!!) }.fold({ true to it }, { false to (it.message ?: "صار خطأ") }); busy = false } },
                ) { Text("اقفلها") }
            } else FingerprintButton(
                "اقفلها بالبصمة", "انقفلت", listOf("بصمتك", "يتأكد إنها نايمة", "يقفلها"), enabled = !s.busy,
                action = { onStep -> onStep(0); actions.stopSession(s) { onStep(1) } },
                onResult = { ok, m -> msg = ok to m },
            )
            if (s.busy) Text("شغّالة الحين (آخر 10 دقايق)، فما تنقفل عشان ما ينقطع شغل.", color = Tone.warn, style = MaterialTheme.typography.bodySmall)
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
