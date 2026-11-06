package com.notes.purenoteit

import app.cash.turbine.test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PureNoteIt - Flow & Coroutines Turbine Reaktif Akış Testi.
 * Kurallar dokümanındaki 'Flow testleri Turbine ile yapılır' şartının
 * altyapısal olarak hazır olduğunu ve sıfır gecikmeyle çalıştığını doğrular.
 */
class FlowTurbineArchitectureTest {

    @Test
    fun `flow verisi turbine ile sirayla ve kayipsiz dinlenmelidir`() = runTest {
        val testFlow = flowOf("Not 1", "Not 2", "Not 3")

        testFlow.test {
            assertEquals("Not 1", awaitItem())
            assertEquals("Not 2", awaitItem())
            assertEquals("Not 3", awaitItem())
            awaitComplete()
        }
    }
}
