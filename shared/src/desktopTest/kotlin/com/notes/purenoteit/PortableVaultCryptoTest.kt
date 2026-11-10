package com.notes.purenoteit

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import kotlin.experimental.xor

class PortableVaultCryptoTest {

    private val crypto = SharedCryptoFactory.createVaultCrypto()

    @Test
    fun `encryptWithPassphrase benzersiz tuz ve IV ile ENC_P3_PWD uretmeli ve dogru parolayla cozmelidir`() {
        val secret = "Çok gizli çapraz cihaz not içeriği 🔐".toCharArray()
        val payload = """{"title":"Kasa Notu","blocks":["A","B"]}"""
        val pass = "SuperStrongSyncKey#2026".toCharArray()

        val enc1 = crypto.encryptWithPassphrase(payload, pass)
        val enc2 = crypto.encryptWithPassphrase(payload, pass)
        assertTrue(enc1 is AppResult.Success)
        assertTrue(enc2 is AppResult.Success)

        val cipher1 = (enc1 as AppResult.Success).data
        val cipher2 = (enc2 as AppResult.Success).data
        assertTrue(cipher1.startsWith("ENC_P3_PWD:"))
        assertNotEquals("Her şifreleme rastgele salt+IV kullanmalıdır", cipher1, cipher2)

        val wrong = crypto.decryptEnvelope(cipher1, "WrongPass".toCharArray())
        assertTrue(wrong is AppResult.Failure)
        assertEquals(DataError.Security.DECRYPTION_FAILED, (wrong as AppResult.Failure).error)

        val dec = crypto.decryptEnvelope(cipher1, pass)
        assertTrue(dec is AppResult.Success)
        assertEquals(payload, (dec as AppResult.Success).data)
        secret.fill('\u0000')
    }

    @Test
    fun `bos parola ile bulut sifrelemesi reddedilmelidir`() {
        val res = crypto.encryptWithPassphrase("test", charArrayOf())
        assertTrue(res is AppResult.Failure)
        assertEquals(DataError.Security.KEY_REQUIRED, (res as AppResult.Failure).error)
    }

    @Test
    fun `PVM3 medya sifrelemesi butunluk ihlalini ve basliksiz enjeksiyonu reddetmelidir`() {
        val media = ByteArray(512) { idx -> ((idx * 13) xor 0x3C).toByte() }
        val pass = "MediaMasterPass!".toCharArray()

        val encRes = crypto.encryptMediaBytes(media, pass)
        assertTrue(encRes is AppResult.Success)
        val encBytes = (encRes as AppResult.Success).data
        assertFalse(media.contentEquals(encBytes))

        val strictOk = crypto.decryptMediaBytesStrict(encBytes, pass)
        assertTrue(strictOk is AppResult.Success)
        assertArrayEquals(media, (strictOk as AppResult.Success).data)

        // Tampered GCM tag must fail strictly
        val tampered = encBytes.copyOf()
        tampered[tampered.lastIndex] = tampered.last() xor 0xFF.toByte()
        val strictFail = crypto.decryptMediaBytesStrict(tampered, pass)
        assertTrue(strictFail is AppResult.Failure)
        assertEquals(DataError.Security.DECRYPTION_FAILED, (strictFail as AppResult.Failure).error)

        // Plaintext bytes injected as remote blob without PVM3 header must fail strictly
        val injectedPlain = "NOT_ENCRYPTED_IMAGE".encodeToByteArray()
        val unauthenticated = crypto.decryptMediaBytesStrict(injectedPlain, pass)
        assertTrue(unauthenticated is AppResult.Failure)
        assertEquals(DataError.Security.INTEGRITY_MISMATCH, (unauthenticated as AppResult.Failure).error)

        // Legacy compat helper keeps GeneralSecurityException throw on tampered bytes
        var threw = false
        try {
            crypto.decryptMediaBytesLegacyCompat(tampered, pass)
        } catch (_: GeneralSecurityException) {
            threw = true
        }
        assertTrue(threw)
    }
}
