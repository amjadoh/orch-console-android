package com.amjadoh.sirfari

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * الاتصال بسيرفري عن طريق Tailscale بس (نفس الرابط اللي يفتحه المتصفح). ما فيه توكن ولا سر:
 * السيرفر يعرفك من حساب Tailscale. الطلبات اللي تكتب ما تنعاد لحالها أبدا (لو انقطع الاتصال
 * نقرا الحالة من جديد بدل ما نرسل مرة ثانية)، والسيرفر بعد يرفض التحدي المستخدم.
 */
class Api(private val base: String = BASE) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    class Failure(message: String, val status: Int = 0) : Exception(message)

    suspend fun get(path: String): JSONObject = call(Request.Builder().url(base + path).get().build())

    suspend fun post(path: String, body: JSONObject): JSONObject = call(
        Request.Builder().url(base + path)
            .header("Origin", base) // السيرفر يشترطه مع X-Orch (حماية المتصفحات من الطلبات المزورة)
            .header("X-Orch", "1")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    )

    private suspend fun call(req: Request): JSONObject = withContext(Dispatchers.IO) {
        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (!res.isSuccessful) throw Failure(json?.optString("error")?.takeIf { it.isNotBlank() } ?: "خطأ ${res.code}", res.code)
                json ?: throw Failure("رد غير متوقع من السيرفر")
            }
        } catch (e: IOException) {
            // السبب الفعلي معه (اسم الخطأ وأول سطر)، عشان نعرف وش صاير بدل رسالة عامة
            val why = listOfNotNull(e.javaClass.simpleName, e.message?.take(120)).joinToString(": ")
            throw Failure("ما وصلت للسيرفر. تأكد إن Tailscale شغال\n($why)")
        }
    }

    companion object {
        const val BASE = "https://amjad-server.tail6b6055.ts.net"
        const val RP_ID = "amjad-server.tail6b6055.ts.net"
    }
}
