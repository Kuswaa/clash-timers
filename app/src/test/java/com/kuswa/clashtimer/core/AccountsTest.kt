package com.kuswa.clashtimer.core

import com.kuswa.clashtimer.core.SampleExports.names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan 1.1 §5.4: per-account storage keyed by village tag, and the migration from 1.0.2. */
class AccountsTest {
    private val textA = SampleExports.text("export_5.json")      // #TEST005, TH19
    private val textB = SampleExports.text("alt_export_1.json")  // #TEST006, TH16, later timestamp
    private val tA = SampleExports.export5.timestamp
    private val tB = SampleExports.alt1.timestamp
    private val settings = AppSettings()

    private fun sync(accounts: Accounts, text: String?) = AccountSync.apply(accounts, text)
    private fun withTimestamp(text: String, from: Long, to: Long) = text.replace("\"timestamp\":$from", "\"timestamp\":$to")

    @Test fun `syncing account B keeps account A intact`() {
        val (a, oA) = sync(Accounts(), textA)
        assertTrue(oA is SyncLogic.Outcome.Synced)
        assertEquals("#TEST005", a.current)
        // Player taps on A's widget: research potion + Apprentice on a builder job.
        val jobA = SampleExports.export5.builderJobs.first()
        val tapped = a.updateCurrent {
            WidgetActions.toggleHelper(WidgetActions.addBoost(it, BoostType.RESEARCH_POTION, tA + 60, settings.boosts), jobA.key)
        }
        val stateA = tapped.byTag.getValue("#TEST005")
        assertEquals(jobA.key, stateA.links.apprenticeJobKey)

        val (ab, oB) = sync(tapped, textB)
        assertTrue(oB is SyncLogic.Outcome.Synced)
        assertEquals("#TEST006", ab.current)                // the widget shows the latest sync
        assertEquals(stateA, ab.byTag.getValue("#TEST005")) // A untouched: export, links, potions, marks
        assertEquals(setOf("#TEST005", "#TEST006"), ab.byTag.keys)
        assertEquals("#TEST006", ab.currentState.export!!.tag)
        assertEquals(3, ab.currentState.boosts.size) // B's own potions only

        // Syncing A again switches the widget back, with A's links carried over as usual.
        val (back, _) = sync(ab, withTimestamp(textA, tA, tA + 600))
        assertEquals("#TEST005", back.current)
        assertEquals(jobA.key, back.currentState.links.apprenticeJobKey)
        assertEquals(ab.byTag.getValue("#TEST006"), back.byTag.getValue("#TEST006"))
    }

    @Test fun `stale export check is per tag`() {
        val (a, _) = sync(Accounts(), textA)
        val (ab, _) = sync(a, textB)
        // Older than A's own last export → rejected, A's data stays, the shown account says "Sync failed".
        val (stale, o) = sync(ab, withTimestamp(textA, tA, tA - 1))
        assertTrue(o is SyncLogic.Outcome.Failed)
        assertEquals(ab.byTag.getValue("#TEST005"), stale.byTag.getValue("#TEST005"))
        assertEquals("#TEST006", stale.current)
        assertTrue(stale.currentState.syncFailed)
        // Older than B's export but newer than A's → fine, since only A's own timestamp counts.
        assertTrue(tA + 100 < tB)
        val (ok, o2) = sync(stale, withTimestamp(textA, tA, tA + 100))
        assertTrue(o2 is SyncLogic.Outcome.Synced)
        assertEquals("#TEST005", ok.current)
        assertFalse(ok.currentState.syncFailed)
    }

    @Test fun `clipboard that isn't village data marks only the shown account`() {
        val (ab, _) = sync(sync(Accounts(), textA).first, textB)
        val (failed, o) = sync(ab, "hello")
        assertTrue(o is SyncLogic.Outcome.Failed)
        assertTrue(failed.byTag.getValue("#TEST006").syncFailed)
        assertFalse(failed.byTag.getValue("#TEST005").syncFailed)
        // Before any account exists the failure is kept too.
        val (none, _) = sync(Accounts(), null)
        assertNull(none.current)
        assertTrue(none.currentState.syncFailed)
        // The first good sync clears it.
        assertFalse(sync(none, textA).first.currentState.syncFailed)
    }

    @Test fun `migration from the 1_0_2 single state`() {
        val old = (SyncLogic.apply(AppState(), SampleExports.text("export_4.json")) as SyncLogic.Outcome.Synced).state
            .let { WidgetActions.addBoost(it, BoostType.BUILDER_BITE, SampleExports.export4.timestamp + 10, settings.boosts) }
            .copy(notified = setOf("BUILDER:1000027:12#0"))
        val legacyJson = old.encode()

        val migrated = Accounts.load(accountsJson = null, legacyStateJson = legacyJson)
        assertEquals("#TEST000", migrated.current)
        val state = migrated.byTag.getValue("#TEST000")
        assertEquals(old.exportJson, state.exportJson)
        assertEquals(old.links, state.links)
        assertEquals(old.boosts, state.boosts)
        assertEquals(old.firstRemaining, state.firstRemaining)
        assertEquals(old.notified, state.notified)

        // Once accounts are stored they win over any leftover legacy key.
        val stored = Accounts(byTag = mapOf("#Z" to AppState()), current = "#Z").encode()
        assertEquals("#Z", Accounts.load(stored, legacyJson).current)
        // Nothing stored at all, or a legacy state without an export (e.g. only "sync failed").
        assertEquals(Accounts(), Accounts.load(null, null))
        assertTrue(Accounts.load(null, AppState(syncFailed = true).encode()).currentState.syncFailed)
        // Round trip.
        assertEquals(migrated, Accounts.decode(migrated.encode()))
    }

    @Test fun `notifications for every account, named by Town Hall when there are several`() {
        val (ab, _) = sync(sync(Accounts(), textA).first, textB)
        // B is shown, A is in the background. At B's T0 + 3600 B's pet (T0 + 3593) and research (T0 + 3432) are done.
        val now = tB + 3600
        val (marked, notices) = AccountNotices.check(ab, settings, names, now)
        assertTrue(notices.any { it.title == "Pet house free · TH16" && it.jobKey.startsWith("#TEST006/") })
        // Marked per account: the next check is quiet.
        assertTrue(AccountNotices.check(marked, settings, names, now + 5).second.isEmpty())
        // A (not shown) keeps counting: its Apprentice is ready at A's T0 + 71069 = B's T0 + 53938.
        val (_, later) = AccountNotices.check(marked, settings, names, tA + 71069)
        assertTrue(later.any { it.title == "Builder's Apprentice is ready · TH19" && it.jobKey.startsWith("#TEST005/") })
        // The wake-up alarm is the earliest over both accounts.
        val earliest = listOf(ab.byTag.getValue("#TEST005"), ab.byTag.getValue("#TEST006"))
            .mapNotNull { FinishNotices.alarmAt(it, settings, tB) }.min()
        assertEquals(earliest, AccountNotices.alarmAt(ab, settings, tB))
    }

    @Test fun `one account keeps the plain notification titles`() {
        val (a, _) = sync(Accounts(), textA)
        val (_, notices) = AccountNotices.check(a, settings, names, tA + 71069)
        assertEquals(listOf("Builder's Apprentice is ready"), notices.map { it.title })
    }
}
