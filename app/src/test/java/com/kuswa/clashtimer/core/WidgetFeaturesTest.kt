package com.kuswa.clashtimer.core

import com.kuswa.clashtimer.core.SampleExports.names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Plan 1.1 §5: Study Soup button, finish-time display, compact / one-line layouts. */
class WidgetFeaturesTest {
    private val settings = AppSettings()
    private val alt = (SyncLogic.apply(AppState(), SampleExports.text("alt_export_1.json")) as SyncLogic.Outcome.Synced).state
    private val t0 = SampleExports.alt1.timestamp // Mon 16 Nov 2026 20:31:48 UTC

    private fun build(state: AppState = alt, now: Long = t0 + 60, s: AppSettings = settings) =
        WidgetModelBuilder.build(state, s, names, SampleExports.times, now, ZoneOffset.UTC)

    @Test fun `one Study Soup button speeds up both the lab and the pets`() {
        val idle = build()
        assertEquals("Soup" to false, idle.studySoup.text to idle.studySoup.active)
        val souped = WidgetActions.apply(alt, WidgetActions.ADD_BOOST, BoostType.STUDY_SOUP.name, t0 + 60, settings.boosts)
        assertEquals(BoostUse(BoostType.STUDY_SOUP, t0 + 60, t0 + 3660), souped.boosts.last())
        val m = build(souped)
        assertEquals("1h" to true, m.studySoup.text to m.studySoup.active)
        assertTrue(m.studySoup.canUndo)
        // Lab (25x -> 28x) and pet (24x -> 27x) finish sooner; builders don't change.
        val before = build()
        assertTrue(m.labRow.finishAt!! < before.labRow.finishAt!!)
        assertTrue(m.petRow.finishAt!! < before.petRow.finishAt!!)
        assertEquals(before.builderRows.map { it.finishAt }, m.builderRows.map { it.finishAt })
        // Stacks like Bite: a second tap queues right after; the "−" takes the last hour off.
        val twice = WidgetActions.addBoost(souped, BoostType.STUDY_SOUP, t0 + 120, settings.boosts)
        assertEquals(BoostUse(BoostType.STUDY_SOUP, t0 + 3660, t0 + 7260), twice.boosts.last())
        assertEquals(souped.boosts, WidgetActions.undoBoost(twice, BoostType.STUDY_SOUP, t0 + 120).boosts)
        assertEquals("Study Soup +1 h", WidgetActions.describe(alt, souped, WidgetActions.ADD_BOOST, BoostType.STUDY_SOUP.name, names))
    }

    @Test fun `finish time display styles`() {
        val lab = build().labRow // own research finishes at T0 + 3432 = Mon 21:29:00 UTC
        assertEquals("Mon 21:29", lab.finishClock)
        assertEquals("57m", lab.statusText(CountdownStyle.TIME_LEFT))
        assertEquals("Mon 21:29", lab.statusText(CountdownStyle.FINISH_TIME))
        assertEquals("57m · Mon 21:29", lab.statusText(CountdownStyle.BOTH))
        // Days in "Both": no seconds.
        val barracks = build().builderRows.single { it.name == "Squad Barracks" }
        assertEquals("1d 10h · Wed 07:29", barracks.statusText(CountdownStyle.BOTH))
        // Finished and idle rows ignore the style.
        val done = build(now = t0 + 4000).labRow
        assertEquals("Completed", done.statusText(CountdownStyle.FINISH_TIME))
    }

    @Test fun `finish time redraws only when something changes, Both when its text changes`() {
        val now = t0 + 60
        val m = build(now = now)
        val nowMs = now * 1000
        val finishOnly = WidgetSchedule.nextUpdateMs(m, nowMs, liveCountdown = true, style = CountdownStyle.FINISH_TIME)!!
        val both = WidgetSchedule.nextUpdateMs(m, nowMs, liveCountdown = true, style = CountdownStyle.BOTH)!!
        val ticking = WidgetSchedule.nextUpdateMs(m, nowMs, liveCountdown = true, style = CountdownStyle.TIME_LEFT)!!
        assertTrue(both <= finishOnly)
        assertTrue(ticking <= finishOnly)
        // "57m" of the lab row changes 12 s from now (3372 s left → 3360 s = 56m).
        assertEquals((now + 12) * 1000, both.coerceAtMost((now + 12) * 1000))
    }

    @Test fun `setting is stored and old settings default to time left`() {
        assertEquals(CountdownStyle.TIME_LEFT, AppSettings.decode("""{"liveCountdown":false}""").countdownStyle)
        val s = AppSettings(countdownStyle = CountdownStyle.BOTH)
        assertEquals(s, AppSettings.decode(s.encode()))
    }

    @Test fun `layout mode by widget height`() {
        val rows = 10
        assertEquals(LayoutMode.NORMAL, WidgetLayout.mode(null, rows))
        assertEquals(LayoutMode.NORMAL, WidgetLayout.mode(WidgetLayout.normalHeightDp(rows), rows))
        assertEquals(LayoutMode.COMPACT, WidgetLayout.mode(WidgetLayout.normalHeightDp(rows) - 1, rows))
        assertEquals(LayoutMode.COMPACT, WidgetLayout.mode(WidgetLayout.compactHeightDp(rows), rows))
        assertEquals(LayoutMode.SUMMARY, WidgetLayout.mode(WidgetLayout.compactHeightDp(rows) - 1, rows))
        assertEquals(LayoutMode.SUMMARY, WidgetLayout.mode(120, rows))
        assertTrue(WidgetLayout.compactHeightDp(rows) < WidgetLayout.normalHeightDp(rows))
    }

    @Test fun `one line per section shows the next finish of each`() {
        val m = build()
        val (b, l, p) = m.summaryRows
        // Goblin Builder (T0 + 111) is the next builder finish; Goblin research (T0 + 1781) beats the lab's own.
        assertEquals(m.builderRows.first(), b)
        assertTrue(b.goblin)
        assertEquals(m.labGoblinRow, l)
        assertEquals(m.petRow, p)
        // Later: Goblins done; the lab's own research is the next lab finish.
        val later = build(now = t0 + 2000)
        assertFalse(later.summaryRows.first.goblin)
        assertEquals(later.labRow, later.summaryRows.second)
        // No builder job at all: an idle "Builders" row.
        val noBuilders = (SyncLogic.apply(AppState(), """{"tag":"#X","timestamp":100,"buildings":[]}""") as SyncLogic.Outcome.Synced).state
        assertEquals(RowKind.IDLE, build(noBuilders, 100).summaryRows.first.kind)
    }
}
