package com.kuswa.clashtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan 1.1 §2: who each potion and snack affects, and that speeds add up
 * (`rate = 1 + Σ(multiplier − 1)`), with helpers never multiplied by potions or snacks.
 */
class BoostRulesTest {
    private val t0 = 1_000_000L
    private val h = 3600L

    /**
     * One regular builder job (Apprentice lvl 8 on it, session running for the first hour), the Goblin
     * Builder, the lab's own research (Lab Assistant lvl 12, session running), the Goblin Researcher
     * and a pet. All have 1 000 000 s left, so nothing finishes inside the first hour.
     */
    private val export = (ExportParser.parse(
        """{"tag":"#RULES","timestamp":$t0,
            "helpers":[{"data":93000000,"lvl":8,"helper_cooldown":80000},{"data":93000001,"lvl":12,"helper_cooldown":80000}],
            "buildings":[{"data":1000011,"lvl":5,"timer":1000000,"helper_timer":3600,"helper_recurrent":true},
                         {"data":1000012,"lvl":5,"timer":1000000,"extra":true}],
            "units":[{"data":4000001,"lvl":5,"timer":1000000,"helper_timer":3600,"helper_recurrent":true},
                     {"data":4000000,"lvl":5,"timer":1000000,"extra":true}],
            "pets":[{"data":73000000,"lvl":1,"timer":1000000}]}""",
    ) as ParseResult.Success).export
    private val links = HelperLinks.fromExport(export)

    private val builder = export.regularBuilderJobs.single()
    private val goblinBuilder = export.builderJobs.single { it.isGoblin }
    private val lab = export.labJob!!
    private val goblinLab = export.goblinLabJob!!
    private val pet = export.petJob!!

    private fun hour(vararg types: BoostType) = types.map { BoostUse(it, t0, t0 + h) }

    /** Average speed over the first hour (work done / 3600 s). */
    private fun speed(job: Job, boosts: List<BoostUse>, linked: HelperLinks = HelperLinks()): Double {
        val left = TimerEngine().remainingAt(job, export, linked, boosts, t0 + h)
        return (job.remainingAtT0 - left).toDouble() / h
    }

    @Test fun `who each potion and snack affects`() {
        fun affected(type: BoostType) = listOf(builder, goblinBuilder, lab, goblinLab, pet).filter { type.affects(it) }
        assertEquals(listOf(builder, goblinBuilder), affected(BoostType.BUILDER_POTION))
        assertEquals(listOf(builder, goblinBuilder), affected(BoostType.BUILDER_BITE))
        assertEquals(listOf(lab, goblinLab), affected(BoostType.RESEARCH_POTION))
        assertEquals(listOf(pet), affected(BoostType.PET_POTION))
        assertEquals(listOf(lab, goblinLab, pet), affected(BoostType.STUDY_SOUP))
    }

    @Test fun `Study Soup is 4x on the lab, the Goblin Researcher and the pet house, not on builders`() {
        val soup = hour(BoostType.STUDY_SOUP)
        assertEquals(4.0, speed(lab, soup), 1e-9)
        assertEquals(4.0, speed(goblinLab, soup), 1e-9)
        assertEquals(4.0, speed(pet, soup), 1e-9)
        assertEquals(1.0, speed(builder, soup), 1e-9)
        assertEquals(1.0, speed(goblinBuilder, soup), 1e-9)
    }

    @Test fun `Study Soup does not multiply the Lab Assistant hour`() {
        // Lab Assistant lvl 12 (+12) and soup (+3) add up: 1 + 12 + 3 = 16x, not 13 x 4 = 52x.
        assertEquals(13.0, speed(lab, emptyList(), links), 1e-9)
        assertEquals(16.0, speed(lab, hour(BoostType.STUDY_SOUP), links), 1e-9)
    }

    @Test fun `research potion speeds up the Goblin Researcher, the Lab Assistant never does`() {
        assertEquals(24.0, speed(goblinLab, hour(BoostType.RESEARCH_POTION), links), 1e-9)
        // The engine ignores a Lab Assistant link that points at the Goblin's research.
        val wrongLink = HelperLinks(labAssistantJobKey = goblinLab.key)
        assertEquals(1.0, speed(goblinLab, emptyList(), wrongLink), 1e-9)
    }

    @Test fun `Bite and builder potion speed up the Goblin Builder but not the Apprentice`() {
        assertEquals(10.0, speed(goblinBuilder, hour(BoostType.BUILDER_POTION)), 1e-9)
        assertEquals(2.0, speed(goblinBuilder, hour(BoostType.BUILDER_BITE)), 1e-9)
        // Apprentice lvl 8 (+8) + builder potion (+9) = 18x, not 9 x 10 = 90x; + Bite (+1) = 19x.
        assertEquals(9.0, speed(builder, emptyList(), links), 1e-9)
        assertEquals(18.0, speed(builder, hour(BoostType.BUILDER_POTION), links), 1e-9)
        assertEquals(19.0, speed(builder, hour(BoostType.BUILDER_POTION, BoostType.BUILDER_BITE), links), 1e-9)
    }

    @Test fun `stacking is additive - potion plus Bite is 11x, research potion plus soup is 27x`() {
        assertEquals(11.0, speed(builder, hour(BoostType.BUILDER_POTION, BoostType.BUILDER_BITE)), 1e-9)
        assertEquals(11.0, speed(goblinBuilder, hour(BoostType.BUILDER_POTION, BoostType.BUILDER_BITE)), 1e-9)
        assertEquals(27.0, speed(lab, hour(BoostType.RESEARCH_POTION, BoostType.STUDY_SOUP)), 1e-9)
        assertEquals(27.0, speed(goblinLab, hour(BoostType.RESEARCH_POTION, BoostType.STUDY_SOUP)), 1e-9)
        assertEquals(27.0, speed(pet, hour(BoostType.PET_POTION, BoostType.STUDY_SOUP)), 1e-9)
    }

    @Test fun `Study Soup multiplier is a setting`() {
        val settings = BoostSettings(studySoupSavingSec = 5 * h)
        val left = TimerEngine(settings).remainingAt(pet, export, HelperLinks(), hour(BoostType.STUDY_SOUP), t0 + h)
        assertEquals(6.0, (pet.remainingAtT0 - left).toDouble() / h, 1e-9)
    }

    @Test fun `old settings with the removed Goblin switch still decode`() {
        val s = AppSettings.decode("""{"boosts":{"boostsApplyToGoblin":false,"builderPotionSavingSec":36000}}""")
        assertEquals(36000L, s.boosts.builderPotionSavingSec)
        assertEquals(3 * h, s.boosts.studySoupSavingSec)
        assertTrue(BoostType.BUILDER_POTION.affects(goblinBuilder))
        assertFalse(BoostType.STUDY_SOUP.affects(goblinBuilder))
    }
}
