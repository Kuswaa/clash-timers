package com.kuswa.clashtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.1 findings (plan §1): `export_5.json` (Kuswa, TH19, 16 Nov 2026) and `alt_export_1.json`
 * (second account, TH16, 17 Nov 2026) with the Goblin Builder, the Goblin Researcher, all three
 * potions and both helpers mid-session.
 */
class Export5Test {
    private val export5 = SampleExports.export5
    private val alt = SampleExports.alt1
    private val names = SampleExports.names

    private fun synced(text: String): AppState =
        (SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced).state

    // ---- export_5.json: TH19 ----

    @Test fun `export 5 parses as TH19 with six builder jobs, one lab and one pet`() {
        assertEquals("#TEST005", export5.tag)
        assertEquals(1794843977L, export5.timestamp)
        assertEquals(19, export5.townHallLevel)
        assertEquals(6, export5.regularBuilderJobs.size)
        assertTrue(export5.builderJobs.none { it.isGoblin })
        assertEquals(4000001, export5.labJob!!.dataId)
        assertTrue(export5.labJob!!.helperLinked)
        assertNull(export5.goblinLabJob)
        assertEquals(73000009, export5.petJob!!.dataId)
        // Two Spiky Towers (1000107) under construction: lvl 0 + timer, two distinct keys.
        val spiky = export5.builderJobs.filter { it.dataId == 1000107 }
        assertEquals(2, spiky.size)
        assertTrue(spiky.all { it.isNewBuilding })
        assertEquals(listOf(0, 1), spiky.map { it.occurrence })
    }

    @Test fun `export 5 has only the Builder Base clock tower boost, so no potion windows`() {
        assertNull(export5.builderBoostRemaining)
        assertNull(export5.labBoostRemaining)
        assertNull(export5.petBoostRemaining)
        assertTrue(synced(SampleExports.text("export_5.json")).boosts.isEmpty())
    }

    @Test fun `export 5 lab job finishes with the Lab Assistant lvl 12 hour`() {
        val s = synced(SampleExports.text("export_5.json"))
        // 167346 s left; 71069 s at 1x, one hour at 13x (46800 s), then 49477 s at 1x.
        assertEquals(export5.timestamp + 124146, TimerEngine().finishTime(export5.labJob!!, export5, s.links, s.boosts))
    }

    // ---- alt_export_1.json: TH16, everything running at once ----

    @Test fun `alt export reads all three potion keys`() {
        assertEquals("#TEST006", alt.tag)
        assertEquals(16, alt.townHallLevel) // Town Hall entry has an unknown "weapon" key
        assertEquals(3595L, alt.builderBoostRemaining)
        assertEquals(3590L, alt.labBoostRemaining)
        assertEquals(3593L, alt.petBoostRemaining)
    }

    @Test fun `sync turns lab_boost and pet_boost into potion windows from T0`() {
        val t0 = alt.timestamp
        assertEquals(
            setOf(
                BoostUse(BoostType.BUILDER_POTION, t0, t0 + 3595),
                BoostUse(BoostType.RESEARCH_POTION, t0, t0 + 3590),
                BoostUse(BoostType.PET_POTION, t0, t0 + 3593),
            ),
            synced(SampleExports.text("alt_export_1.json")).boosts.toSet(),
        )
    }

    @Test fun `exported lab and pet potions replace tapped ones, tapped snacks are kept`() {
        val t0 = alt.timestamp
        val old = synced(SampleExports.text("export_5.json")).copy(
            boosts = listOf(
                BoostUse(BoostType.RESEARCH_POTION, t0 - 100, t0 + 3500), // in lab_boost now → replaced
                BoostUse(BoostType.PET_POTION, t0 - 100, t0 + 3500),      // in pet_boost now → replaced
                BoostUse(BoostType.BUILDER_BITE, t0 - 100, t0 + 3500),    // not exported → kept, counted once
            ),
        )
        val s = (SyncLogic.apply(old, SampleExports.text("alt_export_1.json")) as SyncLogic.Outcome.Synced).state
        assertEquals(1, s.boosts.count { it.type == BoostType.RESEARCH_POTION })
        assertEquals(BoostUse(BoostType.RESEARCH_POTION, t0, t0 + 3590), s.boosts.single { it.type == BoostType.RESEARCH_POTION })
        assertEquals(BoostUse(BoostType.PET_POTION, t0, t0 + 3593), s.boosts.single { it.type == BoostType.PET_POTION })
        assertEquals(BoostUse(BoostType.BUILDER_BITE, t0 - 100, t0 + 3500), s.boosts.single { it.type == BoostType.BUILDER_BITE })
    }

    @Test fun `the lab has two jobs, the Goblin Researcher's is extra`() {
        assertEquals(2, alt.labJobs.size)
        assertEquals(4000001, alt.labJob!!.dataId)        // own research, Lab Assistant on it
        assertFalse(alt.labJob!!.isGoblin)
        assertEquals(3455L, alt.labJob!!.helperTimer)
        val goblin = alt.goblinLabJob!!
        assertEquals(4000000, goblin.dataId)
        assertEquals(42732L, goblin.remainingAtT0)
        assertTrue(goblin.isGoblin)
        // Goblin Builder too: a building with "extra": true.
        assertEquals(listOf(1000008), alt.builderJobs.filter { it.isGoblin }.map { it.dataId })
    }

    @Test fun `Prospector without helper_cooldown parses and does not break the shared cooldown`() {
        val prospector = alt.helper(GameIds.HELPER_PROSPECTOR)!!
        assertEquals(1, prospector.level)
        assertNull(prospector.cooldown)
        assertEquals(82651L, alt.helper(GameIds.HELPER_BUILDERS_APPRENTICE)!!.cooldown)
        assertEquals(82651L, alt.helperCooldown)
        assertEquals(2, alt.helper(GameIds.HELPER_BUILDERS_APPRENTICE)!!.level)
        assertEquals(1, alt.helper(GameIds.HELPER_LAB_ASSISTANT)!!.level)
    }

    @Test fun `helper links come from helper_recurrent, never on the Goblin's research`() {
        val s = synced(SampleExports.text("alt_export_1.json"))
        assertEquals(alt.jobs.single { it.dataId == 1000106 }.key, s.links.apprenticeJobKey)
        assertEquals(alt.labJob!!.key, s.links.labAssistantJobKey)
        // Tapping + on the Goblin's research row doesn't move the Lab Assistant there.
        val tapped = WidgetActions.toggleHelper(s, alt.goblinLabJob!!.key)
        assertEquals(s.links, tapped.links)
    }

    @Test fun `alt export finish times with all potions and both helper sessions`() {
        val s = synced(SampleExports.text("alt_export_1.json"))
        val t0 = alt.timestamp
        val engine = TimerEngine()
        fun finish(job: Job) = engine.finishTime(job, alt, s.links, s.boosts)
        // Goblin Builder: 1104 s at 10x.
        assertEquals(t0 + 111, finish(alt.builderJobs.single { it.isGoblin }))
        // Pet: 86214 s at 24x, done inside the potion hour.
        assertEquals(t0 + 3593, finish(alt.petJob!!))
        // Own research: research potion (+23) + Lab Assistant lvl 1 session (+1) = 25x.
        assertEquals(t0 + 3432, finish(alt.labJob!!))
        // Goblin research: research potion only (24x).
        assertEquals(t0 + 1781, finish(alt.goblinLabJob!!))
        // Squad Barracks (new): potion + Apprentice lvl 2 (12x) for 3451 s, potion alone to 3595 s,
        // 1x until the next Apprentice hour at T0 + 82651 (3x), then 1x.
        assertEquals(t0 + 125841, finish(alt.jobs.single { it.dataId == 1000106 }))
    }

    @Test fun `widget shows both lab rows and drops the Goblin's when it is done`() {
        val s = synced(SampleExports.text("alt_export_1.json"))
        val t0 = alt.timestamp
        val m = WidgetModelBuilder.build(s, AppSettings(), names, SampleExports.times, t0 + 60)
        assertEquals(4000001 to RowKind.RUNNING, m.labRow.iconIds.first() to m.labRow.kind)
        assertFalse(m.labRow.goblin)
        val goblinRow = m.labGoblinRow!!
        assertTrue(goblinRow.goblin)
        assertEquals(4000000, goblinRow.iconIds.first())
        assertEquals(RowButton.NONE, goblinRow.button) // no Lab Assistant for the Goblin
        assertEquals(RowButton.LAB_ASSISTANT, m.labRow.button)
        assertEquals(2, m.labRows.size)

        // Goblin research done at T0 + 1781; the own research (T0 + 3432) still runs.
        val later = WidgetModelBuilder.build(s, AppSettings(), names, SampleExports.times, t0 + 2000)
        assertNull(later.labGoblinRow)
        assertEquals(listOf(m.labRow.jobKey), later.labRows.map { it.jobKey })
        assertEquals(RowKind.RUNNING, later.labRow.kind)
    }

    @Test fun `Goblin Researcher notification`() {
        val notice = FinishNotices.notice(alt.goblinLabJob!!, names)
        assertTrue(notice.title, notice.title.startsWith("Goblin Researcher done: "))
    }

    // ---- Parser robustness ----

    @Test fun `unknown keys anywhere are ignored`() {
        val text = """{"tag":"#X","timestamp":100,"brand_new_section":[{"data":1,"timer":5}],
            "emotes":[1,2],"building_variants":[3],"building_variants2":[],"weapon":{"a":1},
            "buildings":[{"data":1000001,"lvl":19,"weapon":3,"cnt":1,"mystery":{"x":[1]}},
                         {"data":1000011,"lvl":5,"timer":50,"gear_up":1,"new_flag":true}],
            "boosts":{"clocktower_boost":5,"something_boost":99}}"""
        val e = (ExportParser.parse(text) as ParseResult.Success).export
        assertEquals(19, e.townHallLevel)
        assertEquals(listOf(1000011), e.jobs.map { it.dataId }) // the unknown section's timer is not a job
        assertNull(e.builderBoostRemaining)
    }

    @Test fun `helper_recurrent false is not a link and a missing cooldown is no crash`() {
        val text = """{"tag":"#X","timestamp":100,"buildings":[],
            "helpers":[{"data":93000000,"lvl":3},{"data":93000001,"lvl":4,"helper_cooldown":10}],
            "units":[{"data":4000001,"lvl":2,"timer":100000,"helper_recurrent":false}]}"""
        val e = (ExportParser.parse(text) as ParseResult.Success).export
        assertFalse(e.labJob!!.helperLinked)
        assertNull(e.helper(GameIds.HELPER_BUILDERS_APPRENTICE)!!.cooldown)
        // Linked by hand to a job whose helper has no cooldown: no future sessions are predicted.
        val noCooldown = """{"tag":"#X","timestamp":100,"buildings":[{"data":1000011,"lvl":5,"timer":7200,"helper_recurrent":true}],
            "helpers":[{"data":93000000,"lvl":3}]}"""
        val e2 = (ExportParser.parse(noCooldown) as ParseResult.Success).export
        val links = HelperLinks.fromExport(e2)
        assertEquals(100L + 7200, TimerEngine().finishTime(e2.jobs.single(), e2, links, emptyList()))
    }

    @Test fun `the export's item list covers every named section`() {
        assertNotNull(alt.items.firstOrNull { it.kind == ItemKind.BUILDING && it.dataId == 1000106 })
        assertNotNull(alt.items.firstOrNull { it.kind == ItemKind.TROOP && it.dataId == 4000000 })
        assertNotNull(alt.items.firstOrNull { it.kind == ItemKind.HERO && it.dataId == 28000001 })
        assertNotNull(alt.items.firstOrNull { it.kind == ItemKind.PET && it.dataId == 73000000 })
        assertNotNull(alt.items.firstOrNull { it.kind == ItemKind.MODULE && it.dataId == 102000040 })
        assertNotNull(export5.items.firstOrNull { it.kind == ItemKind.TROOP && it.dataId == 4000195 })
        assertTrue(alt.items.none { it.dataId == 1000054 }) // Builder Base (buildings2) stays out
    }
}
