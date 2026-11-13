package com.amjadoh.sirfari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** من رد المنظم «ما عرفت المشروع. المشاريع: أ، ب، ج» نطلع الأسماء (نفس الأسماء اللي يفهمها المحلل) */
internal fun projectNames(message: String): List<String> =
    message.substringAfter("المشاريع:", "").split("،").map { it.trim() }.filter { it.isNotBlank() && it.length <= 40 }

/** الأمر اللي ما فهمه المنظم (ما فيه مشروع أو فعل يعرفه): نعرض أزرار المشاريع بدل ما يوقف */
internal fun needsProject(p: JSONObject?): Boolean =
    p != null && !p.optBoolean("sendable") && p.optString("kind") == "error" &&
        p.optString("message").let { it.startsWith("ما فهمت الأمر") || it.startsWith("ما عرفت المشروع") }

/** النص اللي ينرسل فعلا: لو اخترت مشروع، يصير «المشروع: نصك» (المحلل يفهم النقطتين مع اسم مشروع واحد) */
internal fun composed(text: String, project: String?): String = if (project == null) text.trim() else "$project: ${text.trim()}"

private val SESSION_ROW = Regex("^•\\s*(.+?)\\s+—\\s+(.+)$")

/** «أمر جديد»: تكتب بالعربي، ويوريك وش فهم المنظم، وترسله بالبصمة. ومعه الجلسات المفتوحة وزر قفلها. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommandSheet(api: Api, actions: Actions, onClose: () -> Unit, onSent: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var sessions by remember { mutableStateOf<JSONObject?>(null) }

    var pick by remember { mutableStateOf<String?>(null) }
    var projects by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(text, pick) {
        if (text.trim().length < 3) { preview = null; return@LaunchedEffect }
        delay(350)
        preview = runCatching { api.post("/api/command/preview", JSONObject().put("text", composed(text, pick))) }.getOrNull()
    }
    LaunchedEffect(Unit) {
        // أسماء المشاريع من المنظم نفسه (نفس اللي يفهمها المحلل)
        projects = runCatching { projectNames(api.post("/api/command/preview", JSONObject().put("text", "افتح")).optString("message")) }.getOrDefault(emptyList())
    }
    var history by remember { mutableStateOf<org.json.JSONArray?>(null) }
    LaunchedEffect(Unit) {
        sessions = runCatching { api.get("/api/sessions") }.getOrNull()
        history = runCatching { api.get("/api/commands").optJSONArray("commands") }.getOrNull()
    }

    fun run(block: suspend () -> String) {
        if (busy) return
        busy = true; msg = null
        scope.launch {
            msg = runCatching { block() }.fold({ true to it }, { false to (it.message ?: "صار خطأ") })
            busy = false
            sessions = runCatching { api.get("/api/sessions") }.getOrNull() ?: sessions
            if (msg?.first == true) onSent()
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("أمر جديد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (projects.isNotEmpty()) {
                Text("١. اختر المشروع", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    projects.forEach { n -> FilterChip(selected = pick == n, onClick = { pick = if (pick == n) null else n; preview = null; msg = null }, label = { Text(n) }) }
                }
                Text("٢. اكتب وش تبغى بكلامك العادي", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedTextField(
                // المعاينة القديمة تنمسح فورا مع أي تعديل: ما ينرسل شي تحت ملخص قديم
                value = text, onValueChange = { text = it.take(600); msg = null; preview = null}, minLines = 3, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (pick != null) "مثلا: غير لون زر الحفظ للأزرق" else "مثلا: افتح جلسة المصروف على الجوال\nأو: وش الجلسات المفتوحة") },
            )
            preview?.let { p ->
                val sendable = p.optBoolean("sendable")
                val line = if (needsProject(p) && projects.isNotEmpty()) "ما عرفت لأي مشروع. اختر المشروع فوق، ونصك ينرسل له زي ما هو."
                    else p.optString("summary").ifBlank { p.optString("message") }
                Panel {
                    Text(if (sendable) "وش فهم المنظم" else "ما ينرسل", style = MaterialTheme.typography.labelLarge, color = if (sendable) Tone.ok else Tone.warn)
                    Text(line)
                    p.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    p.optString("request").takeIf { it.isNotBlank() }?.let { Text("الطلب: $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (sendable) {
                    val kind = p.optString("kind")
                    val action = p.optString("action")
                    val label = when {
                        kind == "session" && action == "open" -> "افتح الجلسة بالبصمة"
                        kind == "session" && action == "close" -> "سكرها"
                        kind == "session" -> "اعرض الجلسات"
                        else -> "أرسل بالبصمة"
                    }
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        run {
                            when {
                                kind == "session" && action == "open" -> actions.openSession(p.optString("project"))
                                kind == "session" && action == "close" -> actions.sessionNoFingerprint("close", p.optString("project"))
                                kind == "session" -> actions.sessionNoFingerprint("list")
                                else -> actions.command(composed(text, pick))
                            }
                        }
                    }) { Text(label) }
                }
            }
            Text("أسئلة عن السيرفر نفسه: من تبويب السيرفر «اسأل Claude».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error) }

            history?.takeIf { it.length() > 0 }?.let { h ->
                HorizontalDivider()
                Text("آخر أوامرك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                for (i in 0 until minOf(h.length(), 5)) {
                    val c = h.optJSONObject(i) ?: continue
                    val (label, tone) = when (c.optString("state")) { "done" -> "انفتح" to Tone.ok; "pending" -> "ينتظر" to Tone.run; else -> "ما انرسل" to Tone.bad }
                    StatusLine(tone, c.optString("text").take(80), label)
                    c.optString("detail").takeIf { it.isNotBlank() && it != "null" && c.optString("state") != "done" }?.let {
                        Text(it.take(160), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            sessions?.takeIf { it.optBoolean("installed") }?.let { s ->
                HorizontalDivider()
                Text("جلساتك المفتوحة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val lines = s.optString("list").lines()
                val open = lines.mapNotNull { SESSION_ROW.find(it.trim()) }
                if (open.isEmpty()) Text("ما فيه جلسة مفتوحة (أو القائمة قديمة)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                open.forEach { m ->
                    val (proj, used) = m.destructured
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Text(proj); Text(used, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        OutlinedButton(enabled = !busy, onClick = { run { actions.sessionNoFingerprint("close", proj) } }) { Text("قفلها") }
                    }
                }
                lines.firstOrNull { it.contains("الرام") }?.let { Text(it.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton(enabled = !busy, onClick = { run { actions.sessionNoFingerprint("list") } }) { Text("حدث القائمة") }
            }
        }
    }
}
