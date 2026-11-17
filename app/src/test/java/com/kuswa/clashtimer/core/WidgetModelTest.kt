package com.kuswa.clashtimer.core

import com.kuswa.clashtimer.core.SampleExports.export2
import com.kuswa.clashtimer.core.SampleExports.names
import com.kuswa.clashtimer.core.SampleExports.times
import com.kuswa.clashtimer.core.WidgetModelBuilder.COMPLETED
import com.kuswa.clashtimer.core.WidgetModelBuilder.formatAgo
import com.kuswa.clashtimer.core.WidgetModelBuilder.formatRemaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class WidgetModelTest {

    private val state2: AppState = (SyncLogic.apply(AppState(), SampleExports.text("export_2.json"))
        as SyncLogic.Outcome.Synced).state
    private val t0 = export2.timestamp
    private val settings = AppSettings()

    private fun build(now: Long, state: AppState = state2, s: AppSettings = settings, t: UpgradeTimes = times) =
        WidgetModelBuilder.build(state, s, names, t, now, ZoneOffset.UTC)

    @Test fun oneHourAfterExport2() {
        val m = build(t0 + 3600)
        assertTrue(m.hasData)
        assertEquals("Chief TH18", m.title)
        assertEquals("BUILDERS 6/6", m.buildersLabel)
        assertFalse(m.goblinWorking)
        // Sorted by finish time; the module (Apprentice) finishes first at T0 + 74607.
        assertEquals(
            listOf(
                "Cake-A-Pult Damage → 8" to "19:43:27",   // 74607 − 3600 = 71007 s
                "Wizard Tower → 17" to "1d 02:23:22",     // 98602 − 3600 = 95002 s
                "Wizard Tower → 17" to "1d 05:17:15",
                "Super Wizard Tower (new)" to "5d 06:02:38",
                "Firespitter → 3" to "6d 21:14:06",
                "Firespitter → 3" to "10d 03:48:03",
            ),
            m.builderRows.map { it.label to Countdown.text(it.remaining) },
        )
        assertEquals(RowButton.APPRENTICE, m.builderRows[0].button)
        assertTrue(m.builderRows.drop(1).all { it.button == RowButton.LINK })
        // Name and level are separate on the widget (the arrow is drawn as an image).
        assertEquals(Triple("Cake-A-Pult Damage", "8", true), m.builderRows[0].let { Triple(it.name, it.levelText, it.showArrow) })
        assertEquals(Triple("Super Wizard Tower", "(new)", false), m.builderRows[3].let { Triple(it.name, it.levelText, it.showArrow) })
        assertEquals(listOf(102000040, 103000013, 1000097), m.builderRows[0].iconIds) // module, defense, station

        // Dragon with Lab Assistant: T0 + 78685 − 3600 = 75085 s.
        assertEquals("Dragon → 13" to "20:51:25", m.labRow.let { it.label to Countdown.text(it.remaining) })
        assertEquals(RowButton.LAB_ASSISTANT, m.labRow.button)
        assertEquals("Spirit Fox → 8" to "2d 17h", m.petRow.let { it.label to it.staticStatus })

        // 1.1: one line per helper with its next session (T0 + 72189 = Thu 15 Oct 11:48:02 UTC).
        assertEquals(
            listOf(FooterPart("Apprentice: Cake-A-Pult Damage · next 11:48"), FooterPart("Assistant: Dragon · next 11:48")),
            m.footerLeft,
        )
        assertEquals("synced 1h ago", m.footerRight)
        assertFalse(m.syncFailed)
    }

    @Test fun progressFromGameData() {
        // Dragon 12 → 13 takes 15 days (1296000 s); 121885 s were left at T0.
        val atT0 = build(t0)
        assertEquals(1 - 121885 / 1296000f, atT0.labRow.progress, 1e-4f)
        // An hour later at 1× (helper hour not started yet) it has moved by 3600 s of work.
        assertEquals(1 - (121885 - 3600) / 1296000f, build(t0 + 3600).labRow.progress, 1e-4f)
        // During the Lab Assistant's hour the bar moves 13× as fast (work left drops 13 s per second).
        val helperStart = t0 + 72189
        val workAtStart = 121885 - 72189
        assertEquals(1 - (workAtStart - 13 * 600) / 1296000f, build(helperStart + 600).labRow.progress, 1e-4f)
    }

    @Test fun progressFallsBackToRemainingAtFirstSync() {
        fun export(ts: Long, timer: Long) =
            """{"tag":"#T","timestamp":$ts,"buildings":[{"data":1000999,"lvl":3,"timer":$timer}]}"""
        val first = (SyncLogic.apply(AppState(), export(1000, 8000)) as SyncLogic.Outcome.Synced).state
        assertEquals(0f, build(1000, first).builderRows.single().progress, 1e-6f) // unknown ID, first sight
        assertEquals(0.25f, build(3000, first).builderRows.single().progress, 1e-6f)
        val second = (SyncLogic.apply(first, export(5000, 4000)) as SyncLogic.Outcome.Synced).state
        assertEquals(mapOf("BUILDER:1000999:4#0" to 8000L), second.firstRemaining)
        assertEquals(0.5f, build(5000, second).builderRows.single().progress, 1e-6f)
        // No game data at all → the same fallback.
        assertEquals(0.5f, build(5000, second, t = UpgradeTimes.EMPTY).builderRows.single().progress, 1e-6f)
    }

    @Test fun afterModuleFinishes() {
        val m = build(t0 + 74607)
        assertEquals("BUILDERS 5/6", m.buildersLabel)
        val module = m.builderRows.first()
        assertEquals(RowKind.COMPLETED, module.kind)
        assertEquals(COMPLETED, module.staticStatus)
        assertEquals(1f, module.progress)
        assertEquals(RowButton.NONE, module.button)
        // The Apprentice's last session started at T0 + 72189, so it's on cooldown until 23 h after that
        // (T0 + 154989, 80382 s from now). The Lab Assistant still works on the Dragon.
        assertEquals(
            listOf(FooterPart("Apprentice: 23h"), FooterPart("Assistant: Dragon · next 10:48")),
            m.footerLeft,
        )
        assertEquals("synced 20h ago", m.footerRight)
        // Within Finish.GRACE_SEC (2 s) it is already done; 3 s before it is still running (FinishRedrawTest).
        val before = build(t0 + 74604)
        assertEquals("0:00:03", Countdown.text(before.builderRows.first().remaining))
        assertEquals("BUILDERS 6/6", before.buildersLabel)
    }

    @Test fun everythingFinished() {
        val m = build(t0 + 30L * 86400)
        assertEquals("BUILDERS 0/6", m.buildersLabel)
        assertEquals(6, m.builderRows.size) // finished jobs stay until the next sync; free builders get no rows
        assertTrue(m.allRows.all { it.kind == RowKind.COMPLETED })
        assertEquals(
            listOf(FooterPart("Apprentice ready", FooterTone.IDLE, check = true), FooterPart("Assistant ready", FooterTone.IDLE, check = true)),
            m.footerLeft,
        )
        assertEquals("synced 30d ago", m.footerRight)
        assertNull(m.nextChangeAt?.takeIf { it < t0 + 30L * 86400 })
    }

    @Test fun heroesUseShortNamesInTheFooter() {
        val text = """{"tag":"#T","timestamp":100,"buildings":[],
            "helpers":[{"data":93000000,"lvl":8,"helper_cooldown":3600},{"data":93000001,"lvl":12,"helper_cooldown":3600}],
            "heroes":[{"data":28000000,"lvl":98,"timer":50000,"helper_recurrent":true}],
            "units":[{"data":4000023,"lvl":11,"timer":90000,"helper_recurrent":true}]}"""
        val s = (SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced).state
        assertEquals(
            listOf(FooterPart("Apprentice: King · next 01:01"), FooterPart("Assistant: Baby Dragon · next 01:01")),
            build(100, s).footerLeft,
        )
        // Without a `helpers` array the linked jobs still show, just without a next session time.
        val noHelpers = text.replace(Regex(""""helpers":\[[^\]]*\],"""), "")
        val s2 = (SyncLogic.apply(AppState(), noHelpers) as SyncLogic.Outcome.Synced).state
        assertEquals(listOf(FooterPart("Apprentice: King"), FooterPart("Assistant: Baby Dragon")), build(100, s2).footerLeft)
    }

    @Test fun noLabOrPetJobShowsIdleRows() {
        val text = """{"tag":"#T","timestamp":100,"buildings":[{"data":1000001,"lvl":12,"cnt":1},
            {"data":1000015,"lvl":1,"cnt":2},{"data":1000011,"lvl":3,"timer":7200}]}"""
        val s = (SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced).state
        val m = build(100, s)
        assertEquals("Chief TH12", m.title)
        assertEquals("BUILDERS 1/2", m.buildersLabel)
        assertEquals(1, m.builderRows.size)
        assertEquals(Triple("Laboratory", RowKind.IDLE, "Idle"), m.labRow.let { Triple(it.label, it.kind, it.staticStatus) })
        assertEquals(Triple("Pet House", RowKind.IDLE, "Idle"), m.petRow.let { Triple(it.label, it.kind, it.staticStatus) })
        assertEquals(RowButton.NONE, m.labRow.button)
    }

    @Test fun noDataAndSyncFailed() {
        val empty = build(0, AppState())
        assertFalse(empty.hasData)
        assertEquals("Chief", empty.title)

        val failed = build(t0 + 60, state2.copy(syncFailed = true))
        assertTrue(failed.hasData) // old data still shown
        assertEquals(listOf(FooterPart(WidgetModelBuilder.SYNC_FAILED, FooterTone.ERROR)), failed.footerLeft)
    }

    @Test fun potionsAreReflected() {
        val potion = state2.copy(boosts = listOf(BoostUse(BoostType.RESEARCH_POTION, t0, t0 + 3600)))
        // Dragon: 121885 − 23 h (82800) = 39085 s → before the helper hour; helper never needed.
        val m = build(t0, potion)
        assertEquals("10:51:25", Countdown.text(m.labRow.remaining))
        assertEquals("1h" to true, m.researchPotion.text to m.researchPotion.active)
        assertFalse(m.petPotion.active)
    }

    @Test fun initials() {
        assertEquals("IT", WidgetModelBuilder.initials("Inferno Tower"))
        assertEquals("S", WidgetModelBuilder.initials("Scattershot"))
        assertEquals("CD", WidgetModelBuilder.initials("Cake-A-Pult Damage"))
        assertEquals("BH", WidgetModelBuilder.initials("B.O.B's Hut"))
        assertEquals("SW", WidgetModelBuilder.initials("Super Wizard Tower"))
    }

    @Test fun formatting() {
        assertEquals("1m", formatRemaining(1))
        assertEquals("1m", formatRemaining(60))
        assertEquals("2m", formatRemaining(61))
        assertEquals("59m", formatRemaining(3540))
        assertEquals("1h 0m", formatRemaining(3541))
        assertEquals("23h 59m", formatRemaining(86340))
        assertEquals("1d 0h", formatRemaining(86400))
        assertEquals("1d 20h", formatRemaining(86400 + 20 * 3600 + 1800))
        assertEquals("just now", formatAgo(-5))
        assertEquals("just now", formatAgo(59))
        assertEquals("4m ago", formatAgo(240))
        assertEquals("4h ago", formatAgo(4 * 3600 + 59 * 60))
        assertEquals("47h ago", formatAgo(47 * 3600))
        assertEquals("2d ago", formatAgo(2 * 86400))
    }
}
