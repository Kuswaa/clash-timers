package com.kuswa.clashtimer.ui

import com.kuswa.clashtimer.core.AppSettings
import com.kuswa.clashtimer.core.AppState
import com.kuswa.clashtimer.core.RowKind
import com.kuswa.clashtimer.core.SampleExports
import com.kuswa.clashtimer.core.SyncLogic
import com.kuswa.clashtimer.core.WidgetModelBuilder
import org.junit.Assert.*
import org.junit.Test

class TimerDashboardModelTest {
    private val state = (SyncLogic.apply(AppState(), SampleExports.text("export_2.json"))
        as SyncLogic.Outcome.Synced).state
    private val t0 = SampleExports.export2.timestamp

    private fun model(now: Long = t0, data: AppState = state) =
        WidgetModelBuilder.build(data, AppSettings(), SampleExports.names, SampleExports.times, now)

    @Test fun filtersContainOnlyTheirOwnCategories() {
        val model = model()
        assertEquals(8, model.timers(TimerFilter.ALL).size)
        assertEquals(6, model.timers(TimerFilter.BUILDERS).size)
        assertEquals(listOf("Dragon"), model.timers(TimerFilter.LAB).map { it.row.name })
        assertEquals(listOf("Spirit Fox"), model.timers(TimerFilter.PETS).map { it.row.name })
        assertTrue(model.timers(TimerFilter.BUILDERS).all { it.category == TimerFilter.BUILDERS })
    }

    @Test fun allTimersAreSortedByPredictedFinish() {
        val timers = model().timers(TimerFilter.ALL)
        assertEquals("Cake-A-Pult Damage", timers[0].row.name)
        assertEquals("Dragon", timers[1].row.name)
        assertEquals(t0 + 74607, model().nextRunningTimer()?.row?.finishAt)
    }

    @Test fun completedTimersDoNotBecomeTheNextFinish() {
        val model = model(t0 + 74607)
        assertEquals("Dragon", model.nextRunningTimer()?.row?.name)
        assertEquals(RowKind.COMPLETED, model.timers(TimerFilter.ALL).last().row.kind)
    }

    @Test fun noExportMeansNoTimersRatherThanFakeIdleData() {
        val model = model(data = AppState())
        assertTrue(model.timers(TimerFilter.ALL).isEmpty())
        assertNull(model.nextRunningTimer())
    }

    @Test fun allCompletedMeansNoNextFinish() {
        val model = model(t0 + 30L * 86400)
        assertNull(model.nextRunningTimer())
        assertTrue(model.timers(TimerFilter.ALL).all { it.row.kind == RowKind.COMPLETED })
    }

    @Test fun exportedVillageWithoutUpgradesStillShowsIdleLabAndPets() {
        val idle = (SyncLogic.apply(AppState(),
            """{"tag":"#TEST000","timestamp":100,"buildings":[]}""") as SyncLogic.Outcome.Synced).state
        val model = model(100, idle)
        assertTrue(model.timers(TimerFilter.BUILDERS).isEmpty())
        assertEquals(2, model.timers(TimerFilter.ALL).size)
        assertTrue(model.timers(TimerFilter.ALL).all { it.row.kind == RowKind.IDLE })
        assertNull(model.nextRunningTimer())
    }
}
