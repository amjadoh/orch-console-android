package com.amjadoh.sirfari

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * مفتاح الجهاز: P-256 داخل Keystore الجوال (StrongBox لو موجود). ما يطلع من الجوال أبداً،
 * وكل توقيع يحتاج بصمة قوية (BIOMETRIC_STRONG)، ولو انضافت بصمة جديدة للجوال يبطل المفتاح ويحتاج تسجيل من جديد.
 * المحفوظ برا Keystore: معرّف الاعتماد والعدّاد بس (مو أسرار).
 */
class DeviceKey(context: Context) {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val credentialId: String? get() = prefs.getString("credentialId", null)
    val enrolled: Boolean get() = credentialId != null && keystore().containsAlias(ALIAS)

    /** مفتاح جديد (يمسح القديم). يرجع المفتاح العام SPKI ومعرّف اعتماد جديد. */
    fun create(): Pair<PublicKey, ByteArray> {
        val ks = keystore()
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        prefs.edit().clear().apply()
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val pair = try {
            gen.initialize(spec(true)); gen.generateKeyPair()
        } catch (e: StrongBoxUnavailableException) {
            gen.initialize(spec(false)); gen.generateKeyPair()
        }
        val credId = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return pair.public to credId
    }

    fun saveEnrollment(credId: ByteArray) {
        prefs.edit().putString("credentialId", WebAuthnShape.b64u(credId)).putInt("counter", 0).apply()
    }

    fun forget() {
        val ks = keystore()
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        prefs.edit().clear().apply()
    }

    /** العدّاد يزيد مع كل توقيع (السيرفر يرفض لو ما زاد) */
    fun nextCounter(): Int {
        val n = prefs.getInt("counter", 0) + 1
        prefs.edit().putInt("counter", n).apply()
        return n
    }

    /** موقّع جاهز، يشتغل بس بعد البصمة (BiometricPrompt + CryptoObject) */
    fun signer(): Signature = Signature.getInstance("SHA256withECDSA").apply {
        initSign(keystore().getKey(ALIAS, null) as PrivateKey)
    }

    companion object {
        const val ALIAS = "sirfari-device-v1"
    }
}
