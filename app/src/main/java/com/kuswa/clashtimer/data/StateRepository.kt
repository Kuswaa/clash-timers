package com.kuswa.clashtimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kuswa.clashtimer.core.Accounts
import com.kuswa.clashtimer.core.AppSettings
import com.kuswa.clashtimer.core.AppState
import com.kuswa.clashtimer.core.NameMap
import com.kuswa.clashtimer.core.SyncLog
import com.kuswa.clashtimer.core.SyncLogEntry
import com.kuswa.clashtimer.core.UpgradeTimes
import com.kuswa.clashtimer.core.UserItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clash_timers")

/**
 * All persisted state lives in one Preferences DataStore, each part a JSON string under its own key:
 * [Accounts] (one [AppState] per village tag, since 1.1), [AppSettings], the sync log and [UserItems].
 * 1.0.x kept a single [AppState] under `state_json`; it is moved into its account on first use.
 */
class StateRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val accounts: Flow<Accounts> = store.data.map(::accountsOf).distinctUntilChanged()
    /** The account the widget shows (the most recently synced one). */
    val state: Flow<AppState> = accounts.map { it.currentState }.distinctUntilChanged()
    val settings: Flow<AppSettings> = store.data.map { AppSettings.decode(it[SETTINGS]) }.distinctUntilChanged()
    val syncLog: Flow<SyncLog> = store.data.map { SyncLog.decode(it[SYNC_LOG]) }.distinctUntilChanged()

    suspend fun currentAccounts(): Accounts = accounts.first()
    suspend fun currentState(): AppState = state.first()
    suspend fun currentSettings(): AppSettings = settings.first()

    /** Atomically read-modify-write all accounts; returns the value [transform] produced. */
    suspend fun <R> updateAccounts(transform: (Accounts) -> Pair<Accounts, R>): R {
        var result: R? = null
        store.edit { prefs ->
            val (next, r) = transform(accountsOf(prefs))
            prefs[ACCOUNTS] = next.encode()
            prefs.remove(STATE) // migrated (1.0.x single state)
            result = r
        }
        @Suppress("UNCHECKED_CAST")
        return result as R
    }

    /** Atomically read-modify-write the shown account's state; returns the value [transform] produced. */
    suspend fun <R> updateState(transform: (AppState) -> Pair<AppState, R>): R = updateAccounts { accounts ->
        var r: R? = null
        val next = accounts.updateCurrent { state -> transform(state).also { r = it.second }.first }
        @Suppress("UNCHECKED_CAST")
        next to (r as R)
    }

    private fun accountsOf(prefs: Preferences): Accounts = Accounts.load(prefs[ACCOUNTS], prefs[STATE])

    /** Appends to the on-screen sync log; never throws (it is diagnostics only). */
    suspend fun log(source: String, ok: Boolean?, message: String) {
        runCatching {
            store.edit { prefs ->
                val entry = SyncLogEntry(System.currentTimeMillis() / 1000, source, ok, message)
                prefs[SYNC_LOG] = SyncLog.decode(prefs[SYNC_LOG]).add(entry).encode()
            }
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs[SETTINGS] = transform(AppSettings.decode(prefs[SETTINGS])).encode() }
    }

    /** Names and icons the player added on the "Unknown items" screen (all accounts share them). */
    val userItems: Flow<UserItems> = store.data.map { UserItems.decode(it[USER_ITEMS]) }.distinctUntilChanged()

    suspend fun currentUserItems(): UserItems = userItems.first()

    suspend fun updateUserItems(transform: (UserItems) -> UserItems) {
        store.edit { prefs -> prefs[USER_ITEMS] = transform(UserItems.decode(prefs[USER_ITEMS])).encode() }
    }

    private companion object {
        /** 1.0.x single state; read once for the migration, then removed. */
        val STATE = stringPreferencesKey("state_json")
        val ACCOUNTS = stringPreferencesKey("accounts_json")
        val SETTINGS = stringPreferencesKey("settings_json")
        val SYNC_LOG = stringPreferencesKey("sync_log_json")
        val USER_ITEMS = stringPreferencesKey("user_items_json")
    }
}

/** Bundled `assets/names.json` + `assets/names_overrides.json`, loaded once per process. */
object Names {
    @Volatile private var cached: NameMap? = null

    /** Bundled names only (the "Unknown items" screen compares against these). */
    fun get(context: Context): NameMap = cached ?: synchronized(this) {
        cached ?: run {
            val assets = context.applicationContext.assets
            val generated = assets.open("names.json").bufferedReader().use { it.readText() }
            // A broken overrides file must never take the generated names down with it.
            val overrides = runCatching { assets.open("names_overrides.json").bufferedReader().use { it.readText() } }.getOrNull()
            runCatching { NameMap.fromJson(generated, overrides) }.getOrElse { NameMap.fromJson(generated) }
        }.also { cached = it }
    }

    /** What the widget shows: bundled names with the player's own names on top. */
    suspend fun load(context: Context): NameMap =
        get(context).withUser(StateRepository(context).currentUserItems().names)
}

/** Bundled `assets/upgrade_times.json` (full upgrade durations for the progress bars), loaded once. */
object Times {
    @Volatile private var cached: UpgradeTimes? = null

    fun get(context: Context): UpgradeTimes = cached ?: synchronized(this) {
        cached ?: runCatching {
            context.applicationContext.assets.open("upgrade_times.json").bufferedReader().use {
                UpgradeTimes.fromJson(it.readText())
            }
        }.getOrDefault(UpgradeTimes.EMPTY).also { cached = it }
    }
}
