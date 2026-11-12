package com.amjadoh.sirfari

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.security.Signature
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * المرحلة 0: تجربة إن البصمة الأصلية تشتغل مع السيرفر، قبل بناء التطبيق كامل.
 * 1) الاتصال: يقرا /api/overview عن طريق Tailscale.
 * 2) التسجيل: برمز لمرة وحدة من root (نفس تسجيل أي جهاز)، ومفتاح جديد في Keystore.
 * 3) التجربة: /api/passkey/test (ما يوافق على شي ولا يغيّر شي).
 */
class MainActivity : FragmentActivity() {
    private val api = Api()
    private lateinit var device: DeviceKey
    private lateinit var appOrigin: String
    private val openTask = androidx.compose.runtime.mutableStateOf<String?>(null) // من الإشعار

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        device = DeviceKey(this)
        appOrigin = WebAuthnShape.originFor(signingCert())
        val repo = Repo(this, api)
        val actions = Actions(api, Signer(this, device, appOrigin), device)
        Inbox.channels(this)
        Inbox.schedule(this)
        openTask.value = intent?.getStringExtra(Inbox.EXTRA_TASK)
        setContent {
            SirfariTheme {
                val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                SirfariApp(repo, api, actions, openTask) { FingerprintScreen() }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Inbox.EXTRA_TASK)?.let { openTask.value = it }
    }

    private fun signingCert(): ByteArray {
        val si = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            ?: error("ما فيه توقيع")
        val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return certs.first().toByteArray()
    }

    private suspend fun authenticate(sig: Signature, subtitle: String): Signature = suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val s = result.cryptoObject?.signature
                if (s != null) cont.resume(s) else cont.resumeWithException(IllegalStateException("البصمة ما رجّعت موقّع"))
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) cont.resumeWithException(Api.Failure(errString.toString()))
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("بصمة سيرفري")
            .setSubtitle(subtitle)
            .setNegativeButtonText("إلغاء")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(sig))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    private suspend fun checkConnection(): String {
        val o = api.get("/api/overview")
        val c = o.optJSONObject("counts") ?: JSONObject()
        val level = when (o.optJSONObject("health")?.optString("level")) {
            "ok" -> "سليم"
            "watch" -> "فيه شي تتابعه"
            "act" -> "يحتاج تدخل"
            else -> "غير معروف"
        }
        return "متصل ✓\nالسيرفر: $level\nتنتظر موافقتك: ${c.optInt("awaiting")} · شغالة: ${c.optInt("running")} · خلصت اليوم: ${c.optInt("doneToday")}"
    }

    private suspend fun enroll(code: String): String {
        val opts = api.post("/api/enroll/options", JSONObject().put("code", code))
        val challenge = opts.getJSONObject("publicKey").getString("challenge")
        val (publicKey, credId) = device.create()
        val cd = WebAuthnShape.clientData("webauthn.create", challenge, appOrigin, packageName)
        val ad = WebAuthnShape.authData(Api.RP_ID, 0, credId)
        val id = WebAuthnShape.b64u(credId)
        val credential = JSONObject()
            .put("id", id).put("rawId", id).put("type", "public-key")
            .put("response", JSONObject()
                .put("clientDataJSON", WebAuthnShape.b64u(cd))
                .put("authenticatorData", WebAuthnShape.b64u(ad))
                .put("publicKey", WebAuthnShape.b64u(publicKey.encoded))
                .put("publicKeyAlgorithm", WebAuthnShape.ALG_ES256))
        try {
            api.post("/api/enroll/finish", JSONObject().put("code", code).put("challengeId", opts.getString("challengeId")).put("label", "سيرفري الأصلي").put("credential", credential))
        } catch (e: Exception) {
            device.forget()
            throw e
        }
        device.saveEnrollment(credId)
        return "انسجل الجوال ✓ (المفتاح داخل الجوال وما يطلع منه)"
    }

    /** ربط بدون رمز root: نسجّل مفتاح الجوال، ويطلع رمز، وتوافق ببصمتك من تطبيقك الحالي */
    private suspend fun link(onCode: (String) -> Unit): String {
        val opts = api.post("/api/link/options", JSONObject())
        val linkId = opts.getString("linkId")
        val challenge = opts.getJSONObject("publicKey").getString("challenge")
        val (publicKey, credId) = device.create()
        val cd = WebAuthnShape.clientData("webauthn.create", challenge, appOrigin, packageName)
        val ad = WebAuthnShape.authData(Api.RP_ID, 0, credId)
        val id = WebAuthnShape.b64u(credId)
        val credential = JSONObject()
            .put("id", id).put("rawId", id).put("type", "public-key")
            .put("response", JSONObject()
                .put("clientDataJSON", WebAuthnShape.b64u(cd))
                .put("authenticatorData", WebAuthnShape.b64u(ad))
                .put("publicKey", WebAuthnShape.b64u(publicKey.encoded))
                .put("publicKeyAlgorithm", WebAuthnShape.ALG_ES256))
        val code = try {
            api.post("/api/link/register", JSONObject().put("linkId", linkId).put("label", "سيرفري الأصلي").put("credential", credential)).getString("code")
        } catch (e: Exception) { device.forget(); throw e }
        onCode(code)
        repeat(200) { // 10 دقايق
            kotlinx.coroutines.delay(3000)
            val st = runCatching { api.get("/api/link/status?id=$linkId").optString("state") }.getOrNull() ?: return@repeat
            when (st) {
                "done" -> { device.saveEnrollment(credId); return "انربط الجوال ✓ (المفتاح داخل الجوال وما يطلع منه)" }
                "gone" -> { device.forget(); throw Api.Failure("طلب الربط انرفض أو انتهى. جرّب مرة ثانية") }
            }
        }
        device.forget()
        throw Api.Failure("انتهت مهلة الربط. جرّب مرة ثانية")
    }

    private suspend fun testFingerprint(): String {
        val id = device.credentialId ?: throw Api.Failure("سجّل الجوال أول")
        val opts = api.post("/api/passkey/test/options", JSONObject())
        val pk = opts.getJSONObject("publicKey")
        val allowed: JSONArray = pk.getJSONArray("allowCredentials")
        if ((0 until allowed.length()).none { allowed.getJSONObject(it).getString("id") == id }) throw Api.Failure("الجوال مو مسجّل في السيرفر")
        val cd = WebAuthnShape.clientData("webauthn.get", pk.getString("challenge"), appOrigin, packageName)
        val signer = try {
            device.signer()
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            device.forget()
            throw Api.Failure("انضافت بصمة جديدة للجوال، فالمفتاح بطل. سجّل الجوال من جديد")
        }
        val sig = authenticate(signer, "تجربة فقط، ما توافق على شي")
        val ad = WebAuthnShape.authData(Api.RP_ID, device.nextCounter())
        sig.update(WebAuthnShape.toSign(ad, cd))
        val credential = JSONObject()
            .put("id", id).put("rawId", id).put("type", "public-key")
            .put("response", JSONObject()
                .put("clientDataJSON", WebAuthnShape.b64u(cd))
                .put("authenticatorData", WebAuthnShape.b64u(ad))
                .put("signature", WebAuthnShape.b64u(sig.sign())))
        val r = api.post("/api/passkey/test/finish", JSONObject().put("challengeId", opts.getString("challengeId")).put("credential", credential))
        return "السيرفر قبل البصمة ✓ (${r.optString("device")})"
    }

    @Composable
    private fun FingerprintScreen() {
        val scope = rememberCoroutineScope()
        var conn by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
        var enrollMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
        var testMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
        var busy by remember { mutableStateOf(false) }
        var enrolled by remember { mutableStateOf(device.enrolled) }
        var code by remember { mutableStateOf("") }
        var linkCode by remember { mutableStateOf<String?>(null) }

        fun run(set: (Pair<Boolean, String>) -> Unit, block: suspend () -> String) {
            if (busy) return
            busy = true
            scope.launch {
                set(runCatching { block() }.fold({ true to it }, { false to (it.message ?: "صار خطأ") }))
                enrolled = device.enrolled
                busy = false
            }
        }

        Text("البصمة في النسخة الأصلية: مفتاح داخل جوالك ما يطلع منه، والسيرفر يتحقق من كل توقيع. تطبيقك الحالي ما يتأثر.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section("١. الاتصال بالسيرفر", conn) {
            Button(onClick = { run({ conn = it }) { checkConnection() } }, enabled = !busy) { Text("افحص الاتصال") }
        }

        Section("٢. تسجيل الجوال", enrollMsg ?: if (enrolled) true to "الجوال مسجّل ✓" else null) {
            if (!enrolled) {
                Text("الأسهل: اربطه من تطبيقك الحالي. يطلع لك رمز هنا، وتوافق عليه ببصمتك في سيرفري الحالي (الطلبات الإدارية).", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { linkCode = null; run({ enrollMsg = it; linkCode = null }) { link { c -> linkCode = c } } }, enabled = !busy) { Text("اربط من تطبيقك الحالي") }
                linkCode?.let { c ->
                    // الرمز من اليسار لليمين وبدون مسافة: في الواجهة العربية المسافة كانت تقلب ترتيب النصّين
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        Text(c, style = MaterialTheme.typography.displaySmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr, letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp)), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("افتح سيرفري الحالي ← تلقى «📱 جهاز جديد يبي يرتبط»، وتأكد إن الرمز نفسه، ووافق بالبصمة. أنتظرك هنا…", style = MaterialTheme.typography.bodySmall)
                }
                Text("أو برمز تسجيل من root:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().take(11) }, singleLine = true,
                    label = { Text("رمز التسجيل") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Button(onClick = { run({ enrollMsg = it }) { enroll(code.trim()) } }, enabled = !busy && code.trim().length >= 11) { Text("سجّل هالجوال") }
            } else {
                OutlinedButton(onClick = { device.forget(); enrolled = false; enrollMsg = null; testMsg = null }, enabled = !busy) { Text("انسَ المفتاح من الجوال") }
            }
        }

        Section("٣. جرّب البصمة", testMsg) {
            Text("تطلب بصمتك وترسل التوقيع للسيرفر يتحقق منه. ما توافق على شي ولا تغيّر شي.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { run({ testMsg = it }) { testFingerprint() } }, enabled = !busy && enrolled) { Text("جرّب البصمة") }
        }

        Text(appOrigin, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    @Composable
    private fun Section(title: String, result: Pair<Boolean, String>?, content: @Composable () -> Unit) {
        Panel(title) {
            content()
            result?.let { (ok, msg) -> Text(msg, color = if (ok) Tone.ok else MaterialTheme.colorScheme.error) }
        }
    }
}

