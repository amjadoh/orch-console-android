package com.amjadoh.sirfari

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.security.Signature
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * التوقيع بالبصمة: يطلب بصمة قوية من أندرويد، ويوقّع التحدي اللي جا من السيرفر بمفتاح الجوال،
 * ويرجّع الاعتماد بنفس شكل WebAuthn. التحدي ما نحسبه هنا أبداً (السيرفر أو root يحسبه)، فالتطبيق
 * ما يقدر يوقّع على شي غير اللي طلبه السيرفر.
 */
class Signer(private val activity: FragmentActivity, private val device: DeviceKey, val appOrigin: String) {

    /** challenge: base64url من السيرفر، allow: الأجهزة المسموحة (لو الجوال مو منها نوقف قبل البصمة) */
    suspend fun sign(challenge: String, allow: List<String>, subtitle: String, rpId: String = Api.RP_ID): JSONObject {
        val id = device.credentialId ?: throw Api.Failure("سجّل الجوال أول (زر المفتاح فوق)")
        if (allow.isNotEmpty() && id !in allow) throw Api.Failure("هالجوال مو من الأجهزة المسموحة لهالطلب")
        val cd = WebAuthnShape.clientData("webauthn.get", challenge, appOrigin, activity.packageName)
        val signer = try {
            device.signer()
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            device.forget()
            throw Api.Failure("انضافت بصمة جديدة للجوال، فالمفتاح بطل. سجّل الجوال من جديد")
        }
        val sig = authenticate(signer, subtitle)
        val ad = WebAuthnShape.authData(rpId, device.nextCounter())
        sig.update(WebAuthnShape.toSign(ad, cd))
        return JSONObject()
            .put("id", id).put("rawId", id).put("type", "public-key")
            .put("response", JSONObject()
                .put("clientDataJSON", WebAuthnShape.b64u(cd))
                .put("authenticatorData", WebAuthnShape.b64u(ad))
                .put("signature", WebAuthnShape.b64u(sig.sign())))
    }

    /** من publicKey حق السيرفر: { challenge, allowCredentials: [{id}] } */
    suspend fun signOptions(publicKey: JSONObject, subtitle: String): JSONObject {
        val allow = publicKey.optJSONArray("allowCredentials") ?: JSONArray()
        val ids = (0 until allow.length()).map { allow.optJSONObject(it)?.optString("id") ?: allow.optString(it) }
        return sign(publicKey.getString("challenge"), ids, subtitle)
    }

    private suspend fun authenticate(sig: Signature, subtitle: String): Signature = suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
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
}
