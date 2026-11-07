package com.notes.purenoteit

import com.notes.purenoteit.ui.animation.PagerTransitionCoordinator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerTransitionCoordinatorTest {

    @Test
    fun `geri donus animasyonu sirasinda sayfa kaydirildiginda overlay kilitlenmeli ve gecis bitince sifirlanmalidir`() {
        val coordinator = PagerTransitionCoordinator()
        assertFalse(coordinator.suppressOverlay)

        // 1. Geri dönüş animasyonu başladı, henüz sayfa kaydırılmadı -> normal yay ve overlay aktif
        coordinator.updatePagerMovement(moving = false, isTransitionActive = true)
        assertFalse(coordinator.suppressOverlay)

        // 2. Animasyon bitmeden kullanıcı parmağıyla sağdaki sayfaya kaydırdı -> anında overlay bastırılır
        coordinator.updatePagerMovement(moving = true, isTransitionActive = true)
        assertTrue(coordinator.suppressOverlay)
        assertTrue(coordinator.wasInterruptedDuringTransition)

        // 3. Kaydırma dursa bile geçiş animasyonu henüz tamamlanmadıysa kilit korunur (tekrar overlay'e sıçramaz)
        coordinator.updatePagerMovement(moving = false, isTransitionActive = true)
        assertTrue(coordinator.suppressOverlay)

        // 4. Hem geçiş animasyonu hem pager hareketi tamamlandığında kilit temizlenir
        coordinator.updatePagerMovement(moving = false, isTransitionActive = false)
        assertFalse(coordinator.suppressOverlay)
        assertFalse(coordinator.wasInterruptedDuringTransition)
    }
}
