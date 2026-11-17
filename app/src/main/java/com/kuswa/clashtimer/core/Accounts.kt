package com.kuswa.clashtimer.core

import kotlinx.serialization.Serializable

/**
 * Per-account storage (plan 1.1 §5.4): one [AppState] per village `tag`, so syncing a second account
 * never overwrites the first one's export, helper links, potions or notification marks. The widget shows
 * [current], the most recently synced account (an account picker comes in 1.2). Settings stay global.
 */
@Serializable
data class Accounts(
    val byTag: Map<String, AppState> = emptyMap(),
    /** Tag of the account the widget shows; null before the first good sync. */
    val current: String? = null,
    /** State before any account exists (only "sync failed" can be in it). */
    val unsynced: AppState = AppState(),
) {
    /** What the widget shows. */
    val currentState: AppState get() = current?.let(byTag::get) ?: unsynced

    /** Every stored account, the current one first. */
    val all: List<Pair<String, AppState>>
        get() = byTag.entries.sortedBy { if (it.key == current) 0 else 1 }.map { it.key to it.value }

    /** Applies [f] to the account the widget shows (widget taps, notification marks). */
    fun updateCurrent(f: (AppState) -> AppState): Accounts {
        val tag = current
        return if (tag != null && tag in byTag) copy(byTag = byTag + (tag to f(byTag.getValue(tag))))
        else copy(unsynced = f(unsynced))
    }

    fun update(tag: String, f: (AppState) -> AppState): Accounts =
        byTag[tag]?.let { copy(byTag = byTag + (tag to f(it))) } ?: this

    fun encode(): String = AppState.JSON.encodeToString(serializer(), this)

    companion object {
        fun decode(text: String?): Accounts? =
            text?.let { runCatching { AppState.JSON.decodeFromString(serializer(), it) }.getOrNull() }

        /**
         * Stored accounts, or — first start after updating from 1.0.x — the old single state moved into the
         * account of its export's tag. Without an export the old state (e.g. "sync failed") stays unsynced.
         */
        fun load(accountsJson: String?, legacyStateJson: String?): Accounts {
            decode(accountsJson)?.let { return it }
            val old = AppState.decode(legacyStateJson)
            val tag = old.export?.tag ?: return Accounts(unsynced = old)
            return Accounts(byTag = mapOf(tag to old), current = tag)
        }
    }
}

/** Finish / helper-ready notifications for every saved account. */
object AccountNotices {
    /**
     * New notices over all accounts, and [accounts] with each account's `notified` marks updated.
     * With more than one account each title says which village (`· TH16`), and the notification keys
     * carry the tag so two accounts' identical upgrades don't replace each other's notification.
     */
    fun check(accounts: Accounts, settings: AppSettings, names: NameMap, now: Long): Pair<Accounts, List<FinishNotice>> {
        var next = accounts
        val notices = mutableListOf<FinishNotice>()
        val several = accounts.byTag.size > 1
        for ((tag, state) in accounts.all) {
            val r = FinishNotices.check(state, settings, names, now)
            val fresh = r.notices.filter { it.jobKey !in state.notified }
            next = next.update(tag) { it.copy(notified = r.notified) }
            val th = state.export?.townHallLevel?.let { " · TH$it" } ?: " · $tag"
            notices += fresh.map { n ->
                n.copy(jobKey = "$tag/${n.jobKey}", title = if (several) n.title + th else n.title)
            }
        }
        return next to notices
    }

    /** Earliest wake-up over all accounts (finish or helper ready, + [Finish.GRACE_SEC]). */
    fun alarmAt(accounts: Accounts, settings: AppSettings, now: Long): Long? =
        accounts.byTag.values.mapNotNull { FinishNotices.alarmAt(it, settings, now) }.minOrNull()
}

/** Sync for several accounts: the export's tag picks the account it goes into. */
object AccountSync {
    fun apply(accounts: Accounts, clipboard: String?): Pair<Accounts, SyncLogic.Outcome> {
        val tag = (clipboard?.let(ExportParser::parse) as? ParseResult.Success)?.export?.tag
        if (tag == null) {
            // Not village data: nothing to file it under, so the shown account reports the failure.
            val o = SyncLogic.apply(accounts.currentState, clipboard)
            return accounts.updateCurrent { o.state } to o
        }
        // The stale-export check compares with this tag's own last export only.
        val o = SyncLogic.apply(accounts.byTag[tag] ?: AppState(), clipboard)
        return when (o) {
            is SyncLogic.Outcome.Synced ->
                accounts.copy(byTag = accounts.byTag + (tag to o.state), current = tag, unsynced = AppState()) to o
            // Older than this account's last sync: its data stays; the shown account says "Sync failed".
            is SyncLogic.Outcome.Failed -> accounts.updateCurrent { it.copy(syncFailed = true) } to o
        }
    }
}
