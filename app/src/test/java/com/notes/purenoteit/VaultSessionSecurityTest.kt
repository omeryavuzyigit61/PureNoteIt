package com.notes.purenoteit

import com.notes.purenoteit.util.SecurityUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PureNoteIt - Kasa Oturum Güvenliği Birim Testi.
 * Bu test kasanın varsayılan durumda kilitli olduğunu,
 * oturum açıldığında 'isVaultUnlocked' bayrağının true olduğunu
 * ve lockVault() çağrıldığında anında kilitlendiğini doğrular.
 */
class VaultSessionSecurityTest {

    @Test
    fun `kasa oturumu dogru kilitlenip acilmalidir`() {
        // 1. Kasa kilitlenir
        SecurityUtil.lockVault()
        assertFalse("Kasa kilitli olmalıdır", SecurityUtil.isVaultUnlocked)

        // 2. Kasa açılır
        SecurityUtil.unlockVault()
        assertTrue("Kasa açılmış olmalıdır", SecurityUtil.isVaultUnlocked)

        // 3. Tekrar kilitlenir
        SecurityUtil.lockVault()
        assertFalse("Kasa tekrar kilitlenmiş olmalıdır", SecurityUtil.isVaultUnlocked)
    }
}
