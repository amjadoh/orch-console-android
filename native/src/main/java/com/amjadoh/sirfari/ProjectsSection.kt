package com.amjadoh.sirfari

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** مشاريعي: حالة كل مستودع (آخر بناء، والتعديلات اللي ما انحفظت، والـ PRs، و«الجاي باختصار»، واللي ينتظرك) */
@Composable
fun ProjectsSection(api: Api, openTask: (String) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val tick = LocalRefresh.current
    LaunchedEffect(tick) { data = runCatching { api.get("/api/projects") }.getOrNull() }
    val list = data?.optJSONArray("projects") ?: return
    if (list.length() == 0) return
    val uri = LocalUriHandler.current
    Panel("مشاريعي") {
        for (i in 0 until list.length()) {
            val p = list.optJSONObject(i) ?: continue
            val name = p.optString("name")
            val ci = p.optJSONObject("ci")
            val git = p.optJSONObject("git")
            val waiting = p.optJSONArray("waiting")
            val ciTone = when (ci?.optString("conclusion")) { "success" -> Tone.ok; "failure" -> Tone.bad; null, "", "null" -> if (ci?.optString("status") == "in_progress") Tone.run else Tone.idle; else -> Tone.warn }
            val badges = buildList {
                if ((waiting?.length() ?: 0) > 0) add("${waiting!!.length()} تنتظرك")
                if ((git?.optInt("dirty") ?: 0) > 0) add("${git!!.optInt("dirty")} تعديل ما انحفظ")
                if ((git?.optInt("ahead") ?: 0) > 0) add("${git!!.optInt("ahead")} ما انرفع")
            }
            Column(Modifier.fillMaxWidth().clickable { open = if (open == name) null else name }.padding(vertical = 2.dp)) {
                StatusLine(ciTone, name, badges.joinToString(" · ").ifBlank { null })
                p.optString("next").takeIf { it.isNotBlank() && it != "null" }?.let { Text("الجاي: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (open == name) 6 else 1) }
            }
            if (open == name) Column(Modifier.padding(start = 8.dp)) {
                git?.let { g -> Text("الفرع ${g.optString("branch")} · ${g.optString("subject")}", style = MaterialTheme.typography.bodySmall) }
                ci?.let { c -> Text("آخر بناء: ${c.optString("workflow")} · ${c.optString("conclusion").takeIf { it.isNotBlank() && it != "null" } ?: c.optString("status")}", style = MaterialTheme.typography.bodySmall)
                    c.optString("url").takeIf { it.startsWith("https://github.com/") }?.let { u -> TextButton(onClick = { uri.openUri(u) }) { Text("افتح البناء") } } }
                p.optJSONArray("prs")?.let { prs -> for (j in 0 until prs.length()) prs.optJSONObject(j)?.let { pr ->
                    TextButton(onClick = { pr.optString("url").takeIf { it.startsWith("https://github.com/") }?.let(uri::openUri) }) { Text("PR #${pr.optInt("number")}: ${pr.optString("title")}") } } }
                waiting?.let { w -> for (j in 0 until w.length()) w.optJSONObject(j)?.let { t ->
                    TextButton(onClick = { openTask(t.optString("taskId")) }) { Text("تنتظرك: ${t.optString("title")}", fontWeight = FontWeight.SemiBold) } } }
            }
        }
    }
}
