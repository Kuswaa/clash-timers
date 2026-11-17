package com.kuswa.clashtimer.core

import com.kuswa.clashtimer.core.SampleExports.names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Plan 1.1 §3.5: helper footer (working / cooldown / ready), counted forward between syncs, and the ready notice. */
class HelperStatusTest {
    private val settings = AppSettings()
    private fun synced(text: String) = (SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced).state

    private val s5 = synced(SampleExports.text("export_5.json"))
    private val t5 = SampleExports.export5.timestamp
    private val alt = synced(SampleExports.text("alt_export_1.json"))
    private val tAlt = SampleExports.alt1.timestamp

    private fun statuses(state: AppState, now: Long) = HelperStatuses.of(state, settings.boosts, now).associateBy { it.kind }
    private fun footer(state: AppState, now: Long) =
        WidgetModelBuilder.build(state, settings, names, SampleExports.times, now, ZoneOffset.UTC).footerLeft

    @Test fun `working helpers show the job and the next session`() {
        val st = statuses(alt, tAlt)
        val appr = st.getValue(HelperKind.APPRENTICE) as HelperStatus.Working
        assertEquals(1000106, appr.job.dataId)
        assertEquals(tAlt + 82651, appr.nextSession) // T0 + helper_cooldown
        val asst = st.getValue(HelperKind.LAB_ASSISTANT) as HelperStatus.Working
        assertEquals(4000001, asst.job.dataId)
        // 2026-11-16 20:31:48 UTC + 82651 s = 2026-11-17 19:29:19 UTC.
        assertEquals("Assistant: Archer · next 19:29", footer(alt, tAlt)[1].text)
    }

    @Test fun `an unlinked helper is on cooldown, then ready, without a new sync`() {
        // Export 5: no builder job carries helper_recurrent, so the Apprentice has nothing to do.
        val before = statuses(s5, t5 + 71068).getValue(HelperKind.APPRENTICE)
        assertEquals(HelperStatus.Cooldown(HelperKind.APPRENTICE, t5 + 71069), before)
        assertEquals(FooterPart("Apprentice: 20h"), footer(s5, t5)[0]) // 71069 s ≈ 19.7 h, rounded up
        assertEquals(FooterPart("Apprentice: 1m"), footer(s5, t5 + 71068)[0])
        val after = statuses(s5, t5 + 71069).getValue(HelperKind.APPRENTICE)
        assertEquals(HelperStatus.Ready(HelperKind.APPRENTICE, t5 + 71069), after)
        assertEquals(FooterPart("Apprentice ready", FooterTone.IDLE, check = true), footer(s5, t5 + 71069)[0])
    }

    @Test fun `when its job finishes the helper waits for the next 23 h session start`() {
        // Lab Assistant on the Archer: finishes at T0 + 124146; sessions start at T0 + 71069 + k * 82800.
        val finish = t5 + 124146
        assertTrue(statuses(s5, finish - 10).getValue(HelperKind.LAB_ASSISTANT) is HelperStatus.Working)
        assertEquals(HelperStatus.Cooldown(HelperKind.LAB_ASSISTANT, t5 + 71069 + 82800), statuses(s5, finish).getValue(HelperKind.LAB_ASSISTANT))
        assertEquals(FooterPart("Assistant: 9h"), footer(s5, finish)[1]) // 29723 s left
        assertTrue(statuses(s5, t5 + 71069 + 82800).getValue(HelperKind.LAB_ASSISTANT) is HelperStatus.Ready)
    }

    @Test fun `the footer redraws when the cooldown text changes and when the helper becomes ready`() {
        val m = WidgetModelBuilder.build(s5, settings, names, SampleExports.times, t5 + 71069 - 90, ZoneOffset.UTC)
        // 90 s left shows "2m"; it turns "1m" at 60 s left.
        assertEquals("Apprentice: 2m", m.footerLeft[0].text)
        assertEquals(t5 + 71069 - 60, m.nextChangeAt)
    }

    @Test fun `ready notification once, and never for a helper that was ready at export time`() {
        val at = t5 + 71069
        val r = FinishNotices.check(s5, settings, names, at)
        val notice = r.notices.single()
        assertEquals("Builder's Apprentice is ready", notice.title)
        assertTrue(notice.jobKey in r.notified)
        // Next refresh with the notified set saved: nothing new.
        assertTrue(FinishNotices.check(s5.copy(notified = r.notified), settings, names, at + 60).notices.isEmpty())
        // A helper that is free at T0 (no cooldown in the export) is not announced after the sync.
        val readyAtT0 = synced("""{"tag":"#X","timestamp":100,"buildings":[],"helpers":[{"data":93000000,"lvl":8}]}""")
        assertEquals(HelperStatus.Ready(HelperKind.APPRENTICE, 100), statuses(readyAtT0, 100).getValue(HelperKind.APPRENTICE))
        assertTrue(FinishNotices.check(readyAtT0, settings, names, 200).notices.isEmpty())
    }

    @Test fun `the wakeup alarm also fires when a cooldown ends`() {
        // Export 5 at T0: the first thing to happen is the Apprentice's cooldown ending (no job ends sooner).
        val firstJob = SampleExports.export5.jobs.minOf { TimerEngine().finishTime(it, SampleExports.export5, s5.links, s5.boosts) }
        assertTrue(firstJob > t5 + 71069)
        assertEquals(t5 + 71069, FinishNotices.nextFinish(s5, settings, t5))
    }

    @Test fun `Lab Assistant ready notice text`() {
        val n = FinishNotices.readyNotice(HelperStatus.Ready(HelperKind.LAB_ASSISTANT, 5))
        assertEquals("Lab Assistant is ready", n.title)
    }

    @Test fun `the Prospector is never a footer line`() {
        assertEquals(setOf(HelperKind.APPRENTICE, HelperKind.LAB_ASSISTANT), statuses(alt, tAlt).keys)
        assertEquals(2, footer(alt, tAlt).size)
    }

    @Test fun `cooldown text and its edges`() {
        assertEquals("14h", HelperStatuses.formatCooldown(13 * 3600 + 1))
        assertEquals("60m", HelperStatuses.formatCooldown(3600))
        assertEquals("1m", HelperStatuses.formatCooldown(1))
        assertEquals(1000L - 3600, HelperStatuses.cooldownTextEdge(1000, 1000 - 5000))
        assertEquals(1000L - 3540, HelperStatuses.cooldownTextEdge(1000, 1000 - 3600))
        assertNull(HelperStatuses.cooldownTextEdge(1000, 1000))
        assertEquals(100L, HelperStatuses.startAtOrAfter(100, 50, 20))
        assertEquals(150L, HelperStatuses.startAtOrAfter(100, 50, 101))
        assertEquals(150L, HelperStatuses.startAtOrAfter(100, 50, 150))
    }
}
