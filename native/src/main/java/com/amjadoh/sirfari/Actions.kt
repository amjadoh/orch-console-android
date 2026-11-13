package com.amjadoh.sirfari

import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * القرارات بالبصمة (نفس اللي يسويه الويب): خيارات ← بصمة ← إرسال ← متابعة الطلب.
 * لو انقطع الاتصال بعد الإرسال ما نرسل مرة ثانية أبداً: نقول لك تشيّك الحالة، والسيرفر بعد يرفض التحدي المستخدم.
 */
class Actions(val api: Api, private val signer: Signer, private val device: DeviceKey) {

    suspend fun decide(taskId: String, purpose: String, note: String? = null, onStep: (Int) -> Unit = {}): String {
        val body = JSONObject().put("taskId", taskId).put("purpose", purpose)
        if (note != null) body.put("note", note)
        return submitWithFingerprint(body, purpose, onStep)
    }

    suspend fun command(text: String, onStep: (Int) -> Unit = {}): String =
        submitWithFingerprint(JSONObject().put("purpose", "command").put("note", text), "command", onStep)

    private suspend fun submitWithFingerprint(body: JSONObject, purpose: String, onStep: (Int) -> Unit): String {
        onStep(0)
        val o = api.post("/api/action/options", body)
        val credential = signer.signOptions(o.getJSONObject("publicKey"), SUBTITLE[purpose] ?: "قرار في سيرفري")
        onStep(1)
        val submissionId = try {
            api.post("/api/action/finish", JSONObject().put("challengeId", o.getString("challengeId")).put("credential", credential)).getString("submissionId")
        } catch (e: Api.Failure) {
            if (e.status == 0) throw Api.Failure("انقطع الاتصال وقت الإرسال. ما بنعيد الإرسال: شيّك حالة المهمة بعد شوي")
            throw e
        }
        onStep(2)
        var misses = 0
        repeat(60) {
            delay(1000)
            val s = try { api.get("/api/submissions/$submissionId").also { misses = 0 } } catch (e: Api.Failure) {
                if (e.status == 0 && ++misses < 20) return@repeat else throw e
            }
            when (s.optString("state")) {
                "pending" -> Unit
                "approved", "cancelled", "done" -> { onStep(99); return DONE[purpose] ?: "تم" }
                else -> throw Api.Failure(s.optString("detail").ifBlank { "انرفضت" })
            }
        }
        throw Api.Failure("المنظّم ما رد، شيّك بعد شوي")
    }

    // ── الجلسات (Claude على الجوال/اللابتوب) ──
    suspend fun openSession(project: String): String {
        val o = api.post("/api/session/options", JSONObject().put("project", project))
        val credential = signer.signOptions(o.getJSONObject("publicKey"), "افتح جلسة $project")
        val r = api.post("/api/session/request", JSONObject().put("action", "open").put("project", o.getString("project"))
            .put("nonce", o.getString("nonce")).put("createdAt", o.getLong("createdAt")).put("credential", credential))
        return waitSession(r.getString("nonce"))
    }

    /** قفل جلسة بعينها (Claude أو Codex من أي مكان): التحدي يغطي pid ووقت بدايتها، والسيرفر يتأكد إنها نايمة */
    suspend fun stopSession(s: SessionInfo, force: Boolean = false, onSigned: () -> Unit = {}): String {
        val target = JSONObject().put("pid", s.pid).put("start", s.start)
        val o = api.post("/api/session/options", JSONObject().put("action", "stop").put("target", target).put("force", force))
        val credential = signer.signOptions(o.getJSONObject("publicKey"), "اقفل جلسة ${s.project ?: s.label}")
        onSigned()
        val r = api.post("/api/session/request", JSONObject().put("action", "stop").put("target", target).put("force", force)
            .put("nonce", o.getString("nonce")).put("createdAt", o.getLong("createdAt")).put("credential", credential))
        return waitSession(r.getString("nonce"))
    }

    suspend fun sessionNoFingerprint(action: String, project: String = ""): String {
        val r = api.post("/api/session/request", JSONObject().put("action", action).put("project", project))
        return waitSession(r.getString("nonce"))
    }

    private suspend fun waitSession(nonce: String): String {
        repeat(60) {
            delay(1500)
            val s = runCatching { api.get("/api/sessions") }.getOrNull() ?: return@repeat
            val res = s.optJSONArray("results") ?: return@repeat
            for (i in 0 until res.length()) {
                val x = res.optJSONObject(i) ?: continue
                if (x.optString("nonce") == nonce) {
                    val reply = x.optString("reply")
                    if (x.optBoolean("ok")) return reply.ifBlank { "تم" } else throw Api.Failure(reply.ifBlank { "ما انفتحت" })
                }
            }
        }
        throw Api.Failure("خدمة الجلسات ما ردت، شيّك بعد شوي")
    }

    // ── طلبات root ──
    /** «الإجراءات الذكية»: يطلب الوصفة (معرّفها بس)، وينتظر root يفتح طلب ينتظر البصمة، ويرجّعه */
    suspend fun requestRecipe(id: String, reason: String): JSONObject {
        api.post("/api/fix", JSONObject().put("fix", id).put("reason", reason.take(200)))
        repeat(30) {
            delay(700)
            val list = runCatching { api.get("/api/admin").optJSONArray("requests") }.getOrNull() ?: return@repeat
            for (i in 0 until list.length()) {
                val x = list.optJSONObject(i) ?: continue
                if (x.optString("action") == "fix" && x.optJSONObject("args")?.optString("fix") == id && x.optString("state") == "pending") return x
            }
        }
        throw Api.Failure("root ما استلم الطلب، جرّب بعد شوي")
    }

    suspend fun adminApprove(r: JSONObject, onSigned: () -> Unit = {}): String {
        val allow = r.optJSONArray("allowCredentials")
        val ids = if (allow == null) emptyList() else (0 until allow.length()).map { allow.optString(it) }
        if (ids.isNotEmpty() && ids.none { it == currentId() }) {
            throw Api.Failure("جوالك الأصلي مو موثوق عند root للحين. وافق من التطبيق الحالي، أو أضف الجوال عند root (admin-trust)")
        }
        val credential = signer.sign(r.getString("challenge"), ids, "طلب root: ${r.optString("summary").take(60)}", r.optString("rpId", Api.RP_ID))
        api.post("/api/admin/approve", JSONObject().put("id", r.getString("id")).put("credential", credential))
        onSigned()
        repeat(300) {
            delay(2000)
            val cur = runCatching { api.get("/api/admin") }.getOrNull() ?: return@repeat // التثبيت يعيد تشغيل التطبيق: نصبر
            val list = cur.optJSONArray("requests") ?: return@repeat
            for (i in 0 until list.length()) {
                val x = list.optJSONObject(i) ?: continue
                if (x.optString("id") != r.getString("id")) continue
                when (x.optString("state")) {
                    "done" -> return "✅ " + (x.optJSONObject("result")?.optString("note")?.takeIf { it.isNotBlank() && it != "null" } ?: "root نفّذ الطلب")
                    "blocked" -> throw Api.Failure("⏸️ " + (x.optJSONObject("result")?.optString("note")?.takeIf { it.isNotBlank() && it != "null" } ?: "مشغول، ما تغيّر شي. جرّب بعدين"))
                    "failed", "rejected", "expired" -> {
                        val res = x.optJSONObject("result")
                        val steps = res?.optJSONArray("steps")
                        val bad = (0 until (steps?.length() ?: 0)).mapNotNull { steps!!.optJSONObject(it) }.firstOrNull { !it.optBoolean("ok") }
                        val why = listOfNotNull(bad?.optString("name"), bad?.optString("detail")?.takeIf { it.isNotBlank() }).joinToString(": ")
                        throw Api.Failure(mapOf("failed" to "ما نجح", "rejected" to "انرفض", "expired" to "انتهت مهلته")[x.optString("state")] + (if (why.isNotBlank()) " ($why)" else ""))
                    }
                }
            }
        }
        throw Api.Failure("root ما خلص للحين، شيّك بعد شوي")
    }

    suspend fun adminReject(id: String) { api.post("/api/admin/reject", JSONObject().put("id", id)) }

    private fun currentId() = device.credentialId

    /** هل جوالك موثوق عند root لهالطلب؟ (لو لا: نقول له يوافق من المتصفح بدل زر يفشل) */
    fun trustedFor(r: JSONObject): Boolean {
        val allow = r.optJSONArray("allowCredentials") ?: return true
        if (allow.length() == 0) return true
        val me = currentId() ?: return false
        return (0 until allow.length()).any { allow.optString(it) == me }
    }

    companion object {
        val STEPS = mapOf(
            "approve" to listOf("بصمتك", "التحقق من بصمتك", "تسجيل الموافقة وبدء التنفيذ"),
            "cancel" to listOf("بصمتك", "التحقق من بصمتك", "إلغاء المهمة"),
            "revise" to listOf("بصمتك", "التحقق من بصمتك", "المنفذ يعيد الخطة"),
            "switch" to listOf("بصمتك", "التحقق من بصمتك", "تبديل الأدوار"),
            "replan" to listOf("بصمتك", "التحقق من بصمتك", "المنفذ يبدأ يخطط"),
            "stop" to listOf("بصمتك", "التحقق من بصمتك", "إيقاف المنفذ وفك الحجز"),
            "retry" to listOf("بصمتك", "التحقق من بصمتك", "فتح محاولة جديدة"),
            "command" to listOf("بصمتك", "التحقق من بصمتك", "المنظّم يفتح الطلب"),
        )
        val DONE = mapOf(
            "approve" to "✅ انسجلت الموافقة وبدأ التنفيذ",
            "cancel" to "انلغت المهمة",
            "revise" to "✏️ انرسل التعديل، المنفذ يعيد كتابة الخطة",
            "switch" to "🔄 تبدّل المنفذ",
            "replan" to "🔁 المنفذ بيحاول يكتب الخطة من جديد",
            "stop" to "⏹️ وقفت المهمة · الشغل باقي على السيرفر",
            "retry" to "🔁 انفتحت محاولة جديدة",
            "command" to "📨 انرسل للمنظّم، والخطة توصلك للموافقة",
        )
        private val SUBTITLE = mapOf(
            "approve" to "وافق على الخطة وابدأ التنفيذ", "cancel" to "ألغِ المهمة", "revise" to "عدّل الخطة",
            "switch" to "بدّل المنفذ", "replan" to "أعد التخطيط", "stop" to "أوقف المهمة", "retry" to "أعد المحاولة",
            "command" to "أرسل الأمر للمنظّم",
        )
    }
}
