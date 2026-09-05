package com.example.ai_camera

import com.example.ai_camera.ai.AngleAdvice
import com.example.ai_camera.ai.AngleGuidance
import com.example.ai_camera.ai.AngleIssue
import org.junit.Assert.assertEquals
import org.junit.Test

class AngleWindowTest {
    private fun advice(issue: AngleIssue) =
        AngleAdvice(perfect = issue == AngleIssue.NONE, issue = issue, note = "")

    @Test
    fun `starts empty and grows`() {
        val first = AngleGuidance.slidingWindow(emptyList(), advice(AngleIssue.SUBJECT_LEFT))
        assertEquals(listOf(AngleIssue.SUBJECT_LEFT), first.map { it.issue })
    }

    @Test
    fun `keeps one fewer than the window, since the current frame fills the last slot`() {
        var window = emptyList<AngleAdvice>()
        listOf(AngleIssue.SUBJECT_LEFT, AngleIssue.SUBJECT_HIGH, AngleIssue.TOO_FAR)
            .forEach { window = AngleGuidance.slidingWindow(window, advice(it)) }

        assertEquals(AngleGuidance.HISTORY_SCANS - 1, window.size)
    }

    @Test
    fun `drops the oldest check once full`() {
        var window = emptyList<AngleAdvice>()
        listOf(
            AngleIssue.SUBJECT_LEFT,
            AngleIssue.SUBJECT_HIGH,
            AngleIssue.TOO_FAR,
            AngleIssue.NONE,
        ).forEach { window = AngleGuidance.slidingWindow(window, advice(it)) }

        // The two most recent survive, oldest first; SUBJECT_LEFT and SUBJECT_HIGH have aged out.
        assertEquals(listOf(AngleIssue.TOO_FAR, AngleIssue.NONE), window.map { it.issue })
    }

    @Test
    fun `newest check is always last`() {
        var window = emptyList<AngleAdvice>()
        listOf(AngleIssue.SUBJECT_LEFT, AngleIssue.SUBJECT_RIGHT)
            .forEach { window = AngleGuidance.slidingWindow(window, advice(it)) }

        assertEquals(AngleIssue.SUBJECT_RIGHT, window.last().issue)
    }
}
