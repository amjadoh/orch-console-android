package com.amjadoh.sirfari

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun icon(type: String) = when (type) { "video" -> "🎬"; "image" -> "🖼️"; "pdf" -> "📄"; "apk" -> "📦"; else -> "📎" }
private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("ar"))

/**
 * المخرجات (طلب أمجد): مخرجات مو كل الملفات.
 * - مشروع فيه فيديوهات: كل الفيديوهات والصور (تدخل عليه وتشغّلها).
 * - باقي المشاريع: آخر إصدار بس (أحدث APK، ولو ما فيه فأحدث ملف)، وينزل بضغطة.
 */
@Composable
fun FilesScreen(api: Api) {
    var root by rememberSaveable { mutableStateOf<String?>(null) }
    var rootLabel by rememberSaveable { mutableStateOf("") }
    var roots by remember { mutableStateOf<List<JSONObject>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { api.get("/files/api/roots").optJSONArray("roots") }.fold(
            { a -> roots = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it) }; err = null }, { err = it.message })
    }
    err?.let { Panel { Text(it, color = MaterialTheme.colorScheme.error) } }
    if (root != null) { FileList(api, root!!, rootLabel) { root = null }; return }
    val list = roots ?: return
    val media = list.filter { isMedia(it) }
    val releases = list.filter { !isMedia(it) && it.optInt("count") > 0 }
    if (media.isEmpty() && releases.isEmpty()) { Panel { Text("ما فيه مخرجات للحين") }; return }
    if (media.isNotEmpty()) Panel("فيديوهات وصور") {
        media.forEach { r ->
            val t = r.optJSONObject("types")
            Row(
                Modifier.fillMaxWidth().clickable { root = r.optString("id"); rootLabel = r.optString("label") }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🎬")
                Column(Modifier.weight(1f)) {
                    Text(r.optString("label"))
                    Text(listOfNotNull(t?.optInt("video")?.takeIf { it > 0 }?.let { "$it فيديو" }, t?.optInt("image")?.takeIf { it > 0 }?.let { "$it صورة" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("‹", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (releases.isNotEmpty()) Panel("آخر إصدار لكل مشروع") {
        releases.forEach { r -> LatestRelease(api, r) }
    }
}

private fun isMedia(r: JSONObject) = (r.optJSONObject("types")?.optInt("video") ?: 0) > 0

@Composable
private fun LatestRelease(api: Api, r: JSONObject) {
    val ctx = LocalContext.current
    val id = r.optString("id")
    val hasApk = (r.optJSONObject("types")?.optInt("apk") ?: 0) > 0
    var f by remember(id) { mutableStateOf<JSONObject?>(null) }
    var note by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        f = runCatching { api.get("/files/api/list?root=${URLEncoder.encode(id, "UTF-8")}&limit=1" + if (hasApk) "&type=apk" else "").optJSONArray("files")?.optJSONObject(0) }.getOrNull()
    }
    val file = f
    Row(
        Modifier.fillMaxWidth().clickable(enabled = file != null) {
            file ?: return@clickable
            download(ctx, "${Api.BASE}/files/f/$id/${file.optString("id")}", file.optString("name"))
            note = "ينزل… (شوف الإشعارات)"
        }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(icon(file?.optString("type") ?: r.optString("icon")))
        Column(Modifier.weight(1f)) {
            Text(r.optString("label"))
            Text(file?.let { listOf(it.optString("name"), size(it.optDouble("size")), dateFmt.format(Date(it.optLong("mtime")))).joinToString(" · ") } ?: "…",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.ok) }
        }
        if (file != null) Text("⬇", color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileList(api: Api, root: String, label: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var group by rememberSaveable(root) { mutableStateOf<String?>(null) }
    var data by remember(root, group) { mutableStateOf<JSONObject?>(null) }
    var groups by remember(root) { mutableStateOf<List<String>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(root, group) {
        val q = "/files/api/list?root=${URLEncoder.encode(root, "UTF-8")}&limit=60" + (group?.let { "&group=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
        data = runCatching { api.get(q) }.getOrNull()
        if (group == null) {
            groups = runCatching { api.get("/files/api/roots").optJSONArray("roots") }.getOrNull()?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("id") == root }?.optJSONObject("groups")?.keys()?.asSequence()?.toList()
            } ?: emptyList()
        }
    }
    playing?.let { VideoDialog(it) { playing = null } }
    TextButton(onClick = onBack) { Text("→ كل المخرجات") }
    if (groups.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = group == null, onClick = { group = null }, label = { Text("الكل") })
        groups.forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) }) }
    }
    note?.let { Text(it, color = Tone.ok, style = MaterialTheme.typography.bodySmall) }
    val d = data ?: return
    val all = d.optJSONArray("files")
    val files = org.json.JSONArray((0 until (all?.length() ?: 0)).mapNotNull { all!!.optJSONObject(it) }.filter { it.optString("type") == "video" || it.optString("type") == "image" })
    Panel("$label (${files.length()})") {
        for (i in 0 until (files?.length() ?: 0)) {
            val f = files!!.optJSONObject(i) ?: continue
            val url = "${Api.BASE}/files/f/$root/${f.optString("id")}"
            val type = f.optString("type")
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (type == "video") playing = url
                    else if (type == "image") open(ctx, url)
                    else { download(ctx, url, f.optString("name")); note = "ينزل ${f.optString("name")} (شوف الإشعارات)" }
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(icon(type))
                Column(Modifier.weight(1f)) {
                    Text(f.optString("name"), maxLines = 2)
                    val place = f.optString("place").takeIf { it.isNotBlank() && it != "null" }
                    Text(listOfNotNull(size(f.optDouble("size")), dateFmt.format(Date(f.optLong("mtime"))), place).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// التنزيل عن طريق مدير التنزيلات في أندرويد (يمر من Tailscale مثل التطبيق)، ويحفظ في التنزيلات
private fun download(ctx: Context, url: String, name: String) {
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    val req = DownloadManager.Request(Uri.parse("$url?dl=1"))
        .setTitle(safe)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "سيرفري/$safe")
    (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
}
