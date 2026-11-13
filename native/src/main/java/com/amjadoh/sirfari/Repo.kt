package com.amjadoh.sirfari

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * القراءة من السيرفر مع آخر لقطة محفوظة: لو انقطع الاتصال (Tailscale طافي، تنقل بين Wi-Fi والبيانات)
 * نعرض آخر شي وصل مع وقته، بدل شاشة فاضية. اللقطة بيانات عرض بس (ما فيها أسرار)، داخل مجلد التطبيق الخاص.
 */
class Repo(context: Context, private val api: Api) {
    private val dir = File(context.filesDir, "snapshots").apply { mkdirs() }

    data class Result<T>(val data: T?, val at: Long?, val offline: Boolean, val error: String?)

    suspend fun <T> load(path: String, parse: (JSONObject) -> T): Result<T> {
        val file = File(dir, path.trim('/').replace('/', '_') + ".json")
        return try {
            val json = api.get(path)
            val data = parse(json)
            runCatching { file.writeText(json.toString()) }
            Result(data, System.currentTimeMillis(), offline = false, error = null)
        } catch (e: Exception) {
            val cached = runCatching { parse(JSONObject(file.readText())) }.getOrNull()
            Result(cached, if (cached != null) file.lastModified() else null, offline = true, error = e.message ?: "صار خطأ")
        }
    }
}
