package com.amjadoh.sirfari

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.list(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray?.texts(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
private fun JSONObject.s(k: String) = if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotBlank() } else null

/**
 * التشخيص (نفس بطاقات الويب): كل بطاقة تقول وش صاير، والدليل، والسبب، ووش تسوي، ووش يصير لو ما سويت شي.
 * ومعها: حل جاهز بالبصمة (خدمات المنظّم بس، عن طريق root)، و«اسأل Claude» (استشاري بس)، وطلب صيانة (أوامر تشغّلها انت).
 */
@Composable
fun DiagnosisSection(api: Api, onChanged: () -> Unit) {
    var diag by remember { mutableStateOf<JSONObject?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val tick = LocalRefresh.current
    LaunchedEffect(reload, tick) { diag = runCatching { api.get("/api/diagnosis") }.getOrNull() ?: diag }
    val d = diag ?: return
    val cards = d.optJSONArray("cards").list()
    Panel("التشخيص") {
        cards.forEach { c ->
            val key = c.optString("key")
            Column(Modifier.fillMaxWidth().clickable { open = if (open == key) null else key }.padding(vertical = 2.dp)) {
                StatusLine(Tone.level(c.optString("level")), c.optString("title"), if (open == key) "▲" else "▼")
                Text(c.optString("line"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open == key) CardDetail(api, c) { reload++; onChanged() }
        }
        AskClaude(api, "general", "اسأل Claude عن السيرفر كامل")
    }
    MaintenanceMode(api, d.optLong("maintenanceUntil").takeIf { !d.isNull("maintenanceUntil") }) { reload++ }
    MaintPlans(api)
    Incidents(api)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetail(api: Api, c: JSONObject, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        c.s("tip")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        c.optJSONArray("evidence")?.let { ev ->
            for (i in 0 until ev.length()) {
                val pair = ev.optJSONArray(i) ?: continue
                Row { Text(pair.optString(0), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(pair.optString(1), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            }
        }
        c.optJSONObject("cause")?.let { Text("السبب (${it.optString("sure")}): ${it.optString("text")}", style = MaterialTheme.typography.bodySmall) }
        c.s("todo")?.let { Text("وش تسوي: $it", style = MaterialTheme.typography.bodyMedium) }
        c.s("ifNothing")?.let { Text("لو ما سويت شي: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            c.s("fix")?.let { fix ->
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        msg = runCatching { api.post("/api/fix", JSONObject().put("fix", fix).put("reason", c.optString("line").take(200))).optString("label") }
                            .fold({ true to "انفتح طلب «$it». وافق عليه بالبصمة في تبويب المهام (طلبات root)" }, { false to (it.message ?: "صار خطأ") })
                        busy = false; onChanged()
                    }
                }) { Text("الحل الجاهز") }
            }
            if (c.optBoolean("maintenance")) MaintRequest(api, c.optString("key"))
        }
        msg?.let { (ok, m) -> Text(m, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        AskClaude(api, c.optString("key"), "اسأل Claude عن هالبطاقة")
    }
}

// ── «اسأل Claude»: تحليل استشاري (ما ينفذ شي)، حد 10 باليوم ──
@Composable
private fun AskClaude(api: Api, cardKey: String, label: String) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var state by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cardKey) { result = runCatching { api.get("/api/analysis?card=$cardKey").optJSONObject("analysis") }.getOrNull() }
    OutlinedButton(onClick = { shown = !shown }) { Text(label) }
    if (shown) {
        OutlinedTextField(value = q, onValueChange = { q = it.take(300) }, modifier = Modifier.fillMaxWidth(), label = { Text("سؤالك (اختياري)") })
        Button(enabled = state == null, onClick = {
            state = "يرسل…"
            scope.launch {
                try {
                    val id = api.post("/api/analysis", JSONObject().put("cardKey", cardKey).put("question", q.trim())).getString("id")
                    state = "Claude يحلل… (يمكن دقيقة أو دقيقتين)"
                    repeat(90) {
                        delay(3000)
                        val a = runCatching { api.get("/api/analysis/$id").optJSONObject("analysis") }.getOrNull() ?: return@repeat
                        if (a.optString("state") != "pending" && a.optString("state") != "running") { result = a; state = null; return@launch }
                    }
                    state = null
                } catch (e: Exception) { state = null; result = JSONObject().put("state", "failed").put("detail", e.message) }
            }
        }) { Text("اسأل") }
        state?.let { Text(it, color = Tone.run); LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
    result?.let { a -> AnalysisView(a) }
}

@Composable
private fun AnalysisView(a: JSONObject) {
    val r = a.optJSONObject("result")
    if (r == null) { a.s("detail")?.let { Text("ما تم التحليل: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }; return }
    val act = when (r.optString("actNow")) { "now" -> "تحتاج تتصرف الحين" to Tone.bad; "watch" -> "تابعها" to Tone.warn; else -> "ما يحتاج شي" to Tone.ok }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusLine(act.second, "رأي Claude: ${act.first}", "ثقة ${mapOf("high" to "عالية", "medium" to "متوسطة", "low" to "قليلة")[r.optString("confidence")] ?: ""}")
        Text(r.optString("summary"))
        r.s("confirmedCause")?.let { Text("السبب المؤكد: $it", style = MaterialTheme.typography.bodySmall) }
        r.optJSONArray("possibleCauses").texts().forEach { Text("• محتمل: $it", style = MaterialTheme.typography.bodySmall) }
        Text("التوصية: ${r.optString("recommendation")}", fontWeight = FontWeight.SemiBold)
        r.s("why")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        r.optJSONArray("risks").texts().forEach { Text("⚠️ $it", style = MaterialTheme.typography.bodySmall) }
    }
}

// ── طلب صيانة: Claude يكتب خطة وأوامر، وانت تشغّلها بنفسك ──
@Composable
private fun MaintRequest(api: Api, cardKey: String) {
    val scope = rememberCoroutineScope()
    var sent by remember { mutableStateOf<String?>(null) }
    OutlinedButton(enabled = sent == null, onClick = {
        scope.launch { sent = runCatching { api.post("/api/maint", JSONObject().put("cardKey", cardKey)); "انطلب، الخطة تطلع تحت في «طلبات الصيانة»" }.getOrElse { it.message ?: "صار خطأ" } }
    }) { Text(sent ?: "طلب صيانة") }
}

@Composable
private fun MaintPlans(api: Api) {
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { plans = runCatching { api.get("/api/maint").optJSONArray("plans").list() }.getOrDefault(plans) }
    if (plans.isEmpty()) return
    Panel("طلبات الصيانة") {
        plans.forEach { p ->
            val latest = p.optJSONObject("latest")
            val r = latest?.optJSONObject("result")
            Text(p.optString("title"), fontWeight = FontWeight.SemiBold)
            when {
                r != null -> {
                    Text(r.optString("summary"))
                    r.optJSONArray("steps").list().forEachIndexed { i, s ->
                        Text("${i + 1}. ${s.optString("text")}", style = MaterialTheme.typography.bodySmall)
                        s.s("command")?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
                    }
                    r.s("rollback")?.let { Text("الرجوع: $it", style = MaterialTheme.typography.bodySmall) }
                    Text("الأوامر تشغّلها انت بنفسك من الطرفية. التطبيق ما ينفذ شي.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                latest?.optString("state") == "failed" -> Text("ما انكتبت الخطة: ${latest.optString("detail")}", color = MaterialTheme.colorScheme.error)
                else -> Text("Claude يكتب الخطة…", color = Tone.run)
            }
            OutlinedButton(onClick = { scope.launch { runCatching { api.post("/api/maint/${p.optString("id")}/close", JSONObject()) }; reload++ } }) { Text("قفل الطلب") }
        }
    }
}

// ── وضع الصيانة: كتم مؤقت للملاحظات (الحرج يوصل) ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaintenanceMode(api: Api, until: Long?, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val on = until != null && until > System.currentTimeMillis()
    Panel("وضع الصيانة") {
        Text(if (on) "شغّال لين ${java.text.SimpleDateFormat("h:mm a", java.util.Locale("ar")).format(java.util.Date(until!!))}. الملاحظات مكتومة والحرج يوصل." else "يكتم الملاحظات مؤقتاً وانت تشتغل على السيرفر. الحرج يوصل دايم.",
            style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "نص ساعة", 60 to "ساعة", 120 to "ساعتين").forEach { (m, l) ->
                OutlinedButton(onClick = { scope.launch { runCatching { api.post("/api/maintenance", JSONObject().put("minutes", m)) }; onChanged() } }) { Text(l) }
            }
            if (on) Button(onClick = { scope.launch { runCatching { api.post("/api/maintenance", JSONObject().put("minutes", 0)) }; onChanged() } }) { Text("وقّفه") }
        }
    }
}

// ── سجل الأحداث ──
@Composable
private fun Incidents(api: Api) {
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(Unit) { list = runCatching { api.get("/api/incidents").optJSONArray("incidents").list() }.getOrDefault(emptyList()) }
    if (list.isEmpty()) return
    val now = System.currentTimeMillis()
    Panel("آخر الأحداث") {
        list.take(15).forEach { e ->
            val end = if (e.isNull("end")) null else e.optLong("end")
            StatusLine(if (end == null) Tone.level(e.optString("level")) else Tone.idle, e.optString("text"),
                if (end == null) "مستمر" else "انحلت ${ago(end, now)}")
        }
    }
}
