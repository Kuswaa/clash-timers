package com.kuswa.clashtimer.core

import com.kuswa.clashtimer.core.SampleExports.names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan 1.1 §4: TH19 names, category fallbacks, name layers and the "Unknown items" list. */
class UnknownItemsTest {
    private fun synced(text: String) = (SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced).state
    private val s5 = synced(SampleExports.text("export_5.json"))
    private val t5 = SampleExports.export5.timestamp

    private fun build(state: AppState, n: NameMap, now: Long) =
        WidgetModelBuilder.build(state, AppSettings(), n, SampleExports.times, now)

    @Test fun `TH19 names come from names_overrides json`() {
        assertEquals("Squad Barracks", names.nameOrNull(1000106))
        assertEquals("Spiky Tower", names.nameOrNull(1000107))
        assertEquals("Firecracker Tower", names.nameOrNull(1000108))
        assertEquals("Barch Squad", names.nameOrNull(4000195))
        // The generated data doesn't have them (that's why the overrides file exists).
        assertNull(SampleExports.generatedNames.nameOrNull(1000107))
    }

    @Test fun `every job in the 1_1 exports has a real name`() {
        for (e in listOf(SampleExports.export5, SampleExports.alt1)) {
            val unnamed = e.jobs.filterNot(names::isKnown).map { it.dataId }
            assertTrue("${e.tag}: no name for $unnamed", unnamed.isEmpty())
        }
        assertEquals("Spiky Tower (new)", names.label(SampleExports.export5.jobs.first { it.dataId == 1000107 }))
        assertEquals("Squad Barracks (new)", names.label(SampleExports.alt1.jobs.single { it.dataId == 1000106 }))
    }

    @Test fun `overrides beat generated names and user names beat overrides`() {
        val layered = NameMap(generated = mapOf(1 to "Generated", 2 to "Only generated"), overrides = mapOf(1 to "Override"))
        assertEquals("Override", layered.nameOrNull(1))
        assertEquals("Only generated", layered.nameOrNull(2))
        val withUser = layered.withUser(mapOf(1 to "Mine", 3 to "New one", 2 to "  "))
        assertEquals("Mine", withUser.nameOrNull(1))
        assertEquals("Only generated", withUser.nameOrNull(2)) // a blank user name doesn't hide the bundled one
        assertEquals("New one", withUser.nameOrNull(3))
        assertTrue(withUser.hasBundled(1))
        assertFalse(withUser.hasBundled(3)) // still listed under "Unknown items"
    }

    @Test fun `widget row uses overrides, then the player's name`() {
        val spikyKey = SampleExports.export5.jobs.first { it.dataId == 1000107 }.key
        fun row(n: NameMap) = build(s5, n, t5).builderRows.single { it.jobKey == spikyKey }

        val generatedOnly = row(SampleExports.generatedNames)
        assertEquals("New building (new)", generatedOnly.label)
        assertEquals(1000107, generatedOnly.unknownId)
        assertEquals(FallbackIcon.BUILDING, generatedOnly.fallbackIcon)

        val bundled = row(names)
        assertEquals("Spiky Tower (new)", bundled.label)
        assertNull(bundled.unknownId)

        assertEquals("My Spikes (new)", row(names.withUser(mapOf(1000107 to "My Spikes"))).label)
    }

    @Test fun `category fallback names and icons`() {
        val text = """{"tag":"#X","timestamp":100,
            "buildings":[{"data":1000999,"lvl":1,"timer":9000}],
            "traps":[{"data":12000999,"lvl":1,"timer":9001}],
            "heroes":[{"data":28000999,"lvl":1,"timer":9002}],
            "guardians":[{"data":107000999,"lvl":1,"timer":9003}],
            "units":[{"data":4000999,"lvl":1,"timer":9004}],
            "spells":[{"data":26000999,"lvl":1,"timer":9005}],
            "siege_machines":[{"data":4000998,"lvl":1,"timer":9006}],
            "pets":[{"data":73000999,"lvl":1,"timer":9007}]}"""
        val e = (ExportParser.parse(text) as ParseResult.Success).export
        assertEquals(
            listOf(
                "New building → 2", "New trap → 2", "New hero → 2", "New guardian → 2",
                "New squad → 2", "New spell → 2", "New siege machine → 2", "New pet → 2",
            ),
            e.jobs.map(names::label),
        )
        // Only one lab row is shown at a time, so check the icons on the rows the widget draws.
        val m = build(synced(text), names, 100)
        assertEquals(listOf(FallbackIcon.BUILDING, FallbackIcon.BUILDING, FallbackIcon.TROOP, FallbackIcon.TROOP),
            m.builderRows.map { it.fallbackIcon })
        assertEquals(FallbackIcon.SQUAD, m.labRow.fallbackIcon) // unknown troop = a TH19 squad
        assertEquals(FallbackIcon.PET, m.petRow.fallbackIcon)
        assertEquals(FallbackIcon.SPELL, Fallbacks.icon(ItemKind.SPELL, named = false))
        assertEquals(FallbackIcon.TROOP, Fallbacks.icon(ItemKind.TROOP, named = true))
        assertTrue(m.builderRows.all { it.unknownId != null })
    }

    @Test fun `unknown hero keeps its full fallback name in the helper footer`() {
        val s = synced("""{"tag":"#X","timestamp":100,"buildings":[],
            "helpers":[{"data":93000000,"lvl":8,"helper_cooldown":50}],
            "heroes":[{"data":28000999,"lvl":1,"timer":90000,"helper_recurrent":true}]}""")
        assertTrue(build(s, names, 100).footerLeft.first().text.startsWith("Apprentice: New hero"))
    }

    @Test fun `unknown items list items without a name or an icon, once each`() {
        val e = (ExportParser.parse(
            """{"tag":"#X","timestamp":100,
                "buildings":[{"data":1000001,"lvl":19},{"data":1000107,"lvl":0,"timer":5},{"data":1000107,"lvl":0,"timer":6},
                             {"data":1000999,"lvl":1},
                             {"data":1000097,"types":[{"data":103000013,"modules":[{"data":102000040,"lvl":7}]}]}],
                "units":[{"data":4000999,"lvl":1}]}""",
        ) as ParseResult.Success).export
        val icons = setOf(1000001, 1000107, 1000097, 103000013)
        val user = UserItems().withName(4000999, "  Hog Squad  ").copy(icons = setOf(1000999))
        val list = UnknownItems.find(listOf(e, e), names, { it in icons }, user)
        assertEquals(listOf(1000999 to ItemKind.BUILDING, 4000999 to ItemKind.TROOP), list.map { it.item.dataId to it.item.kind })
        val building = list[0]
        assertTrue(building.missingName && building.missingIcon && building.hasUserIcon)
        assertEquals("Hog Squad", list[1].userName)
        // 1000107 is named (overrides) and has an icon; the module has no own icon but its defense has.
        assertFalse(list.any { it.item.dataId == 102000040 })
    }

    @Test fun `user names are trimmed, capped and removed when blank`() {
        val u = UserItems().withName(1, "  A very long name that keeps going and going  ")
        assertEquals(UserItems.MAX_NAME, u.names.getValue(1).length)
        assertEquals(emptyMap<Int, String>(), u.withName(1, "   ").names)
        assertEquals(u, UserItems.decode(u.encode()))
        assertEquals(UserItems(), UserItems.decode("not json"))
    }

    @Test fun `TH19 icons and the Study Soup icon are bundled`() {
        for (f in listOf("1000106.webp", "1000107.webp", "1000108.webp", "4000195.webp", "study_soup.webp")) {
            assertTrue("missing $f", f in SampleExports.iconFiles)
        }
        // So the TH19 items in export 5 are not listed under "Unknown items".
        val bundled = { id: Int -> "$id.webp" in SampleExports.iconFiles || "$id.png" in SampleExports.iconFiles }
        val list = UnknownItems.find(listOf(SampleExports.export5), names, bundled, UserItems())
        assertTrue(list.none { it.item.dataId in setOf(1000106, 1000107, 4000195) })
    }
}
