package com.amjadoh.sirfari

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/**
 * رسائل البصمة بنفس شكل WebAuthn، عشان السيرفر يتحقق منها بنفس الكود اللي يتحقق فيه من بصمة المتصفح
 * (ops/runtime/webauthn.mjs). الفرق الوحيد: المصدر android:apk-key-hash:<sha256 شهادة توقيع التطبيق>.
 * كود صافي (بدون أندرويد) عشان ينختبر في JVM.
 */
object WebAuthnShape {
    const val ALG_ES256 = -7
    private const val FLAG_UP = 0x01
    private const val FLAG_UV = 0x04
    private const val FLAG_AT = 0x40
    private val SAFE = Regex("^[A-Za-z0-9_.:-]{1,200}$")

    fun b64u(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    /** مصدر التطبيق من شهادة توقيعه (DER) */
    fun originFor(signingCertDer: ByteArray): String = "android:apk-key-hash:" + b64u(sha256(signingCertDer))

    private fun safe(s: String): String {
        require(SAFE.matches(s)) { "قيمة غير متوقعة" }
        return s
    }

    /** clientDataJSON: كل القيم من حروف آمنة بس، فما يحتاج ترميز JSON */
    fun clientData(type: String, challenge: String, origin: String, packageName: String): ByteArray =
        "{\"type\":\"${safe(type)}\",\"challenge\":\"${safe(challenge)}\",\"origin\":\"${safe(origin)}\",\"androidPackageName\":\"${safe(packageName)}\"}"
            .toByteArray(Charsets.UTF_8)

    /** authenticatorData: بصمة rpId + الأعلام (حضور + بصمة) + العدّاد، ومع التسجيل معرّف الاعتماد */
    fun authData(rpId: String, counter: Int, credentialId: ByteArray? = null): ByteArray {
        val rpHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        val flags = FLAG_UP or FLAG_UV or (if (credentialId != null) FLAG_AT else 0)
        val extra = if (credentialId != null) 16 + 2 + credentialId.size + 1 else 0
        val buf = ByteBuffer.allocate(37 + extra)
        buf.put(rpHash).put(flags.toByte()).putInt(counter)
        if (credentialId != null) {
            buf.put(ByteArray(16)) // AAGUID: أصفار (بدون شهادة جهاز، مثل attestation "none")
            buf.putShort(credentialId.size.toShort()).put(credentialId)
            buf.put(0xA0.toByte()) // مكان مفتاح COSE: السيرفر ياخذ المفتاح العام من publicKey (SPKI)
        }
        return buf.array()
    }

    /** اللي ينوقّع: authenticatorData + sha256(clientDataJSON) */
    fun toSign(authData: ByteArray, clientData: ByteArray): ByteArray = authData + sha256(clientData)
}
