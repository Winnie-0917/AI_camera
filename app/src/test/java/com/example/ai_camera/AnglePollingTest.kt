package com.example.ai_camera

import com.example.ai_camera.ai.AngleAdvice
import com.example.ai_camera.ai.AngleIssue
import com.example.ai_camera.ai.AnglePolling
import org.junit.Assert.assertEquals
import org.junit.Test

class AnglePollingTest {
    private fun advice(perfect: Boolean) =
        AngleAdvice(perfect = perfect, issue = AngleIssue.NONE, note = "")

    @Test
    fun `polls at the active rate before the first result`() {
        assertEquals(AnglePolling.ACTIVE_MS, AnglePolling.intervalFor(null))
    }

    @Test
    fun `polls at the active rate while the framing needs correcting`() {
        assertEquals(AnglePolling.ACTIVE_MS, AnglePolling.intervalFor(advice(perfect = false)))
    }

    @Test
    fun `backs off once the framing is right`() {
        assertEquals(AnglePolling.SETTLED_MS, AnglePolling.intervalFor(advice(perfect = true)))
    }

    @Test
    fun `speeds back up as soon as the framing drifts again`() {
        assertEquals(AnglePolling.SETTLED_MS, AnglePolling.intervalFor(advice(perfect = true)))
        assertEquals(AnglePolling.ACTIVE_MS, AnglePolling.intervalFor(advice(perfect = false)))
    }

    @Test
    fun `settled rate is slower than the active rate`() {
        assert(AnglePolling.SETTLED_MS > AnglePolling.ACTIVE_MS)
    }

    @Test
    fun `failures back off exponentially instead of hammering a failing api`() {
        assertEquals(AnglePolling.BACKOFF_START_MS, AnglePolling.backoffFor(1))
        assertEquals(AnglePolling.BACKOFF_START_MS * 2, AnglePolling.backoffFor(2))
        assertEquals(AnglePolling.BACKOFF_START_MS * 4, AnglePolling.backoffFor(3))
    }

    @Test
    fun `backoff is capped`() {
        assertEquals(AnglePolling.MAX_BACKOFF_MS, AnglePolling.backoffFor(50))
        assert(AnglePolling.backoffFor(9) <= AnglePolling.MAX_BACKOFF_MS)
    }

    @Test
    fun `a success returns to the normal cadence`() {
        assertEquals(AnglePolling.ACTIVE_MS, AnglePolling.backoffFor(0))
    }
}
