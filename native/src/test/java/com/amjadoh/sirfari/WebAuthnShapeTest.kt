package com.amjadoh.sirfari

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class WebAuthnShapeTest {
    private val rp = "amjad-server.tail6b6055.ts.net"

    @Test
    fun originMatchesServerConstant() {
        // نفس القيمة في ops/runtime/webauthn.mjs (APP_ORIGIN): sha256 الشهادة = البصمة المنشورة
        val certSha = "75C16D7B63A6498598DFEF911F4721C59C143B00C3F403437C8E56730B87B6AC".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals("android:apk-key-hash:dcFte2OmSYWY3--RH0chxZwUOwDD9ANDfI5WcwuHtqw", "android:apk-key-hash:" + WebAuthnShape.b64u(certSha))
    }

    @Test
    fun authDataLayout() {
        val plain = WebAuthnShape.authData(rp, 7)
        assertEquals(37, plain.size)
        assertArrayEquals(WebAuthnShape.sha256(rp.toByteArray()), plain.copyOfRange(0, 32))
        assertEquals(0x05, plain[32].toInt())
        assertEquals(7, ByteBuffer.wrap(plain, 33, 4).int)

        val credId = ByteArray(32) { it.toByte() }
        val reg = WebAuthnShape.authData(rp, 0, credId)
        assertEquals(0x45, reg[32].toInt())
        assertEquals(32, ByteBuffer.wrap(reg, 53, 2).short.toInt())
        assertArrayEquals(credId, reg.copyOfRange(55, 87))
    }

    @Test
    fun clientDataRejectsUnsafeValues() {
        val cd = String(WebAuthnShape.clientData("webauthn.get", "abc_-123", "android:apk-key-hash:x", "com.amjadoh.orch.next"))
        assertTrue(cd.contains("\"origin\":\"android:apk-key-hash:x\""))
        assertThrows(IllegalArgumentException::class.java) { WebAuthnShape.clientData("webauthn.get", "a\"b", "o", "p") }
    }

    @Test
    fun signatureVerifiesLikeServer() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val cd = WebAuthnShape.clientData("webauthn.get", "Y2hhbGxlbmdl", "android:apk-key-hash:x", "com.amjadoh.orch.next")
        val ad = WebAuthnShape.authData(rp, 1)
        val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(kp.private); update(WebAuthnShape.toSign(ad, cd)) }.sign()
        val ok = Signature.getInstance("SHA256withECDSA").apply { initVerify(kp.public); update(ad); update(WebAuthnShape.sha256(cd)) }.verify(sig)
        assertTrue(ok)
    }
}
