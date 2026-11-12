package com.amjadoh.sirfari

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

fun size(bytes: Double): String = when {
    bytes >= 1e9 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1e6 -> "%.0f MB".format(bytes / 1e6)
    bytes >= 1e3 -> "%.0f KB".format(bytes / 1e3)
    else -> "${bytes.toLong()} B"
}
private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun verdictTone(v: String) = when (v) { "protected" -> Tone.run; "used" -> Tone.ok; "review" -> Tone.warn; else -> Tone.idle }
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** مساحة السيرفر: قراءة من لقطة الجرد بس (ما فيه حذف ولا تنظيف). كل عنصر يشرح وش هو ومين يستخدمه ووش يصير لو انحذف. */
@Composable
fun SpaceScreen(api: Api) {
    var path by rememberSaveable { mutableStateOf<String?>(null) }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val back = { path = null; cat = null }

    OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("ابحث باسم ملف أو مجلد") })
    when {
        query.trim().length >= 2 -> SpaceSearch(api, query.trim()) { path = it; query = "" }
        path != null -> SpaceDir(api, path!!, onOpen = { path = it }, onBack = back)
        cat != null -> SpaceCategory(api, cat!!, onOpen = { path = it; cat = null }, onBack = back)
        else -> SpaceSummary(api, onOpen = { path = it }, onCat = { cat = it })
    }
}

@Composable
private fun <T> loadJson(key: Any, block: suspend () -> T): Pair<T?, String?> {
    var data by remember(key) { mutableStateOf<T?>(null) }
    var err by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) { runCatching { block() }.fold({ data = it; err = null }, { err = it.message }) }
    return data to err
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceSummary(api: Api, onOpen: (String) -> Unit, onCat: (String) -> Unit) {
    val (s, err) = loadJson("summary") { api.get("/api/storage") }
    err?.let { Panel { Text(it, color = MaterialTheme.colorScheme.error) } }
    if (s == null) return
    if (!s.optBoolean("ready")) { Panel { Text("أول جرد للمساحة ما خلص للحين. يشتغل كل 6 ساعات.") }; return }
    s.optJSONObject("disk")?.let { d ->
        val pct = d.optDouble("pct")
        Panel("القرص") {
            Bar(pct / 100, Tone.level(d.optString("level")))
            Text("مستخدم ${size(d.optDouble("usedBytes"))} من ${size(d.optDouble("totalBytes"))} (${pct(pct)})", style = MaterialTheme.typography.bodyMedium)
            if (s.optBoolean("stale")) Text("الجرد قديم، الجاي يطلع خلال ساعات", style = MaterialTheme.typography.bodySmall, color = Tone.warn)
        }
    }
    val cats = s.optJSONArray("categories").objs()
    if (cats.isNotEmpty()) Panel("وش ماخذ المساحة") {
        val max = cats.maxOf { it.optDouble("bytes") }.coerceAtLeast(1.0)
        cats.forEach { c ->
            Column(Modifier.fillMaxWidth().clickable { onCat(c.optString("id")) }.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(c.optString("label"), Modifier.weight(1f)); Text(size(c.optDouble("bytes")), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Bar(c.optDouble("bytes") / max, MaterialTheme.colorScheme.primary)
            }
        }
    }
    val sug = s.optJSONArray("suggestions").objs()
    if (sug.isNotEmpty()) Panel("اقتراحات (ما ينحذف شي من هنا)") {
        sug.forEach { x ->
            Column(Modifier.fillMaxWidth().clickable { x.optString("path").takeIf { it.startsWith("/") }?.let(onOpen) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatusLine(if (x.optString("kind") == "review") Tone.warn else Tone.idle, x.optString("title"), size(x.optDouble("bytes")))
                x.optString("why").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                x.optString("effect").takeIf { it.isNotBlank() }?.let { Text("لو انشال: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    val top = s.optJSONArray("top").objs()
    if (top.isNotEmpty()) Panel("أكبر الأماكن") { top.forEach { ItemRow(it, onOpen) } }
    val big = s.optJSONArray("bigFiles").objs()
    if (big.isNotEmpty()) Panel("أكبر الملفات") { big.forEach { ItemRow(it, onOpen) } }
    TextButton(onClick = { onOpen("/") }) { Text("تصفّح القرص كامل ←") }
}

@Composable
private fun ItemRow(x: JSONObject, onOpen: (String) -> Unit) {
    val locked = x.optBoolean("locked")
    val icon = if (locked) "🔒" else if (x.optString("kind") == "dir") "📁" else "📄"
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(x.optString("path")) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(verdictTone(x.optString("verdict")), 8)
        Column(Modifier.weight(1f)) {
            Text("$icon ${x.optString("label").ifBlank { x.optString("name") }}", maxLines = 1)
            Text(x.optString("path"), style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(size(x.optDouble("bytes")), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceDir(api: Api, path: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val (d, err) = loadJson(path) { api.get("/api/storage/dir?p=${enc(path)}") }
    TextButton(onClick = onBack) { Text("→ ملخص المساحة") }
    err?.let { Panel { Text(it, color = MaterialTheme.colorScheme.error) } }
    if (d == null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        d.optJSONArray("crumbs").objs().forEachIndexed { i, c ->
            if (i > 0) Text("‹", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(c.optString("name"), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpen(c.optString("path")) })
        }
    }
    Panel(d.optString("label").ifBlank { d.optString("name").ifBlank { "القرص" } }) {
        Text("${size(d.optDouble("bytes"))}${if (!d.isNull("files")) " · ${d.optInt("files")} ملف" else ""} · ${d.optString("category")}", style = MaterialTheme.typography.bodySmall)
        StatusLine(verdictTone(d.optString("verdict")), d.optString("verdictLabel"))
        d.optString("what").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
        d.optJSONArray("reasons").let { a -> (0 until (a?.length() ?: 0)).forEach { Text("• ${a!!.optString(it)}", style = MaterialTheme.typography.bodySmall) } }
        d.optJSONArray("usedBy").let { a -> if (a != null && a.length() > 0) Text("يستخدمه: " + (0 until a.length()).joinToString("، ") { a.optString(it) }, style = MaterialTheme.typography.bodySmall) }
        d.optJSONObject("git")?.let { g -> Text("Git: ${g.optString("text")}", style = MaterialTheme.typography.bodySmall) }
        d.optString("ifDeleted").takeIf { it.isNotBlank() && it != "null" }?.let { Text("لو انحذف: $it", style = MaterialTheme.typography.bodySmall) }
        d.optString("backup").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (d.optBoolean("locked")) Text("🔒 محمي: ما ينعرض وش داخله", color = Tone.run)
    }
    val kids = d.optJSONArray("children").objs()
    if (kids.isNotEmpty()) Panel("داخله") {
        kids.forEach { ItemRow(it, onOpen) }
        if (d.optDouble("otherBytes") > 0) Text("وباقي أصغر: ${size(d.optDouble("otherBytes"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpaceCategory(api: Api, id: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val (c, err) = loadJson("cat:$id") { api.get("/api/storage/cat?id=${enc(id)}") }
    TextButton(onClick = onBack) { Text("→ ملخص المساحة") }
    err?.let { Panel { Text(it, color = MaterialTheme.colorScheme.error) } }
    if (c == null) return
    Panel("${c.optString("label")} · ${size(c.optDouble("bytes"))}") { c.optJSONArray("items").objs().forEach { ItemRow(it, onOpen) } }
}

@Composable
private fun SpaceSearch(api: Api, q: String, onOpen: (String) -> Unit) {
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(q) {
        delay(350)
        items = runCatching { api.get("/api/storage/find?q=${enc(q)}").optJSONArray("items").objs() }.getOrNull()
    }
    val list = items ?: return
    Panel("النتايج (${list.size})") {
        if (list.isEmpty()) Text("ما لقيت شي في آخر جرد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.take(60).forEach { ItemRow(it, onOpen) }
    }
}

@Composable
fun Bar(fraction: Double, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}
