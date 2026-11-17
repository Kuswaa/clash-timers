package com.kuswa.clashtimer.core

/** The two helpers the widget tracks. */
enum class HelperKind(
    val dataId: Int,
    /** Footer name. */
    val shortName: String,
    /** Notification name. */
    val fullName: String,
    val category: JobCategory,
) {
    APPRENTICE(GameIds.HELPER_BUILDERS_APPRENTICE, "Apprentice", "Builder's Apprentice", JobCategory.BUILDER),
    LAB_ASSISTANT(GameIds.HELPER_LAB_ASSISTANT, "Assistant", "Lab Assistant", JobCategory.LAB);

    fun linkedKey(links: HelperLinks): String? = when (this) {
        APPRENTICE -> links.apprenticeJobKey
        LAB_ASSISTANT -> links.labAssistantJobKey
    }
}

/**
 * What a helper is doing at a moment, counted forward from the export by itself (no sync needed):
 * sessions start at `T0 + helper_cooldown + k · helperPeriodSec` (23 h cycle).
 */
sealed interface HelperStatus {
    val kind: HelperKind

    /** Linked to a job that still runs; the next session starts at [nextSession] (null if unknown). */
    data class Working(override val kind: HelperKind, val job: Job, val nextSession: Long?) : HelperStatus

    /** Not working and its cooldown runs until [readyAt]. */
    data class Cooldown(override val kind: HelperKind, val readyAt: Long) : HelperStatus

    /** Free to be assigned since [readyAt]. */
    data class Ready(override val kind: HelperKind, val readyAt: Long) : HelperStatus
}

object HelperStatuses {

    /** One status per helper present in the export (none for a village without helpers). */
    fun of(state: AppState, settings: BoostSettings, now: Long): List<HelperStatus> {
        val export = state.export ?: return emptyList()
        val engine = TimerEngine(settings)
        val period = settings.helperPeriodSec.coerceAtLeast(settings.helperWindowSec)
        return HelperKind.entries.mapNotNull { kind ->
            val helper = export.helper(kind.dataId)
            val firstSession = helper?.cooldown?.let { export.timestamp + it }
            val job = kind.linkedKey(state.links)
                ?.let { key -> export.jobs.firstOrNull { it.key == key } }
                // The Lab Assistant never works for the Goblin Researcher.
                ?.takeIf { it.category == kind.category && !(kind == HelperKind.LAB_ASSISTANT && it.isGoblin) }
            val finish = job?.let { engine.finishTime(it, export, state.links, state.boosts) }
            if (job != null && finish != null && !Finish.isDone(finish, now)) {
                return@mapNotNull HelperStatus.Working(kind, job, firstSession?.let { startAtOrAfter(it, period, now + 1) })
            }
            // Not unlocked in this village (no entry in `helpers`) and not linked: no footer line.
            if (helper == null) return@mapNotNull null
            // Free from the first session start at or after the moment it had nothing left to do
            // (its job finished, or the export if it had no job). No cooldown in the export = free at T0.
            val idleFrom = maxOf(finish ?: export.timestamp, export.timestamp)
            val readyAt = firstSession?.let { startAtOrAfter(it, period, idleFrom) } ?: export.timestamp
            if (now < readyAt) HelperStatus.Cooldown(kind, readyAt) else HelperStatus.Ready(kind, readyAt)
        }
    }

    /** Smallest `first + k · period` (k ≥ 0) that is ≥ [t]. */
    fun startAtOrAfter(first: Long, period: Long, t: Long): Long {
        if (t <= first) return first
        val k = (t - first + period - 1) / period
        return first + k * period
    }

    /** Cooldown text: `14h`, `42m` (rounded up, so it never shows `0m`). */
    fun formatCooldown(seconds: Long): String {
        val s = seconds.coerceAtLeast(1)
        return if (s > 3600) "${(s + 3599) / 3600}h" else "${(s + 59) / 60}m"
    }

    /** Epoch s when [formatCooldown] of a cooldown ending at [readyAt] next changes, after [now]. */
    fun cooldownTextEdge(readyAt: Long, now: Long): Long? {
        val left = readyAt - now
        if (left <= 0) return null
        val unit = if (left > 3600) 3600L else 60L
        // The text shows ceil(left / unit); it drops when left reaches the next lower multiple of unit.
        val nextLeft = ((left - 1) / unit) * unit
        val edge = readyAt - nextLeft
        return if (edge > now) edge else null
    }
}
