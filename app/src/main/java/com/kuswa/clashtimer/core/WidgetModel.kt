package com.kuswa.clashtimer.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * When a job counts as finished. The widget rows, the notifications and the alarms all use this.
 *
 * A job with [GRACE_SEC] seconds or less left is already finished. So a redraw that lands just
 * before the finish (an alarm delivered early, or `now` truncated to whole seconds) draws
 * "Completed ✓", not a chronometer that would tick past zero into negative numbers.
 */
object Finish {
    const val GRACE_SEC = 2L

    fun isDone(finishAt: Long, now: Long): Boolean = finishAt - now <= GRACE_SEC
}

/**
 * Everything the widget shows. Built without Android so it
 * can be unit tested; the RemoteViews renderer only maps it onto views.
 */
data class WidgetModel(
    val hasData: Boolean,
    val playerName: String,
    val townHall: Int?,
    /** Running regular builder jobs (the Goblin is not counted). */
    val buildersBusy: Int,
    val buildersTotal: Int,
    /** The event Goblin Builder's job is still running: header shows `+ Goblin`. */
    val goblinWorking: Boolean,
    val bite: BoostPill,
    val builderPotion: BoostPill,
    val researchPotion: BoostPill,
    val petPotion: BoostPill,
    /** Study Soup: one button on the LAB line; its window speeds up the lab, the Goblin Researcher and the pets. */
    val studySoup: BoostPill,
    /** One row per builder job (finished ones say Completed); free builders get no rows. */
    val builderRows: List<WidgetRow>,
    /** The laboratory's own research (or `Idle`). */
    val labRow: WidgetRow,
    val petRow: WidgetRow,
    /** Footer lines on the left: one per helper (or the sync-failed message). */
    val footerLeft: List<FooterPart>,
    /** `synced 14m ago`, right side of the footer. */
    val footerRight: String,
    val syncFailed: Boolean,
    /**
     * Next time (epoch s) something other than a countdown's own ticking changes: a job finishing,
     * a potion/helper window edge (progress bars), a pill's minutes, the footer's sync age.
     */
    val nextChangeAt: Long?,
    /** The event Goblin Researcher's research while it runs (GOBLIN row under the lab row); null when done. */
    val labGoblinRow: WidgetRow? = null,
) {
    val buildersLabel: String get() = "BUILDERS $buildersBusy/$buildersTotal"
    val title: String get() = townHall?.let { "$playerName TH$it" } ?: playerName
    /** The LAB section: own research first, then the Goblin Researcher's. */
    val labRows: List<WidgetRow> get() = listOfNotNull(labRow, labGoblinRow)
    val allRows: List<WidgetRow> get() = builderRows + labRows + petRow

    /**
     * The one-line-per-section layout for very small widgets: the next builder job to finish (or, if
     * none runs, the first finished one / "Builders idle"), the next lab job to finish, and the pet row.
     */
    val summaryRows: Triple<WidgetRow, WidgetRow, WidgetRow>
        get() = Triple(
            builderRows.firstOrNull { it.kind == RowKind.RUNNING } ?: builderRows.firstOrNull()
                ?: WidgetModelBuilder.idleRow("Builders", GameIds.BUILDERS_HUT),
            labRows.filter { it.kind == RowKind.RUNNING }.minByOrNull { it.finishAt ?: Long.MAX_VALUE } ?: labRow,
            petRow,
        )
}

/** A potion/snack button on a section line: `Bite` / `+1` when idle, minutes left when running. */
data class BoostPill(
    val type: BoostType,
    val secondsLeft: Long?,
    /** A boost of this type is running or queued, so the small `−` (undo) is shown. */
    val canUndo: Boolean = secondsLeft != null,
) {
    val active: Boolean get() = secondsLeft != null
    val text: String
        get() = secondsLeft?.let(WidgetModelBuilder::formatPill) ?: when (type) {
            BoostType.BUILDER_BITE -> "Bite"
            BoostType.STUDY_SOUP -> "Soup"
            else -> "+1"
        }
}

enum class FooterTone { NORMAL, IDLE, ERROR }

/** One footer line. [check] = draw a ✓ after the text (the widget font has no ✓ glyph, so it's a drawable). */
data class FooterPart(val text: String, val tone: FooterTone = FooterTone.NORMAL, val check: Boolean = false)

enum class RowKind { RUNNING, COMPLETED, IDLE }

/** How a running row shows when it finishes (setting; plan 1.1 §5.2). */
enum class CountdownStyle {
    /** `10:57:12` ticking (or `1d 20h` without the live countdown). Default. */
    TIME_LEFT,
    /** `Thu 18:36`. */
    FINISH_TIME,
    /** `1d 4h · Thu 18:36`, no seconds. */
    BOTH,
}

/** The small button at the right of a row. */
enum class RowButton {
    /** Space kept, nothing drawn (pets, finished rows). */
    NONE,
    /** Green `+`: link the helper here. */
    LINK,
    /** Gold badge: the Builder's Apprentice works on this job. */
    APPRENTICE,
    /** Gold badge: the Lab Assistant works on this job. */
    LAB_ASSISTANT,
}

data class WidgetRow(
    val kind: RowKind,
    /** `Inferno Tower`, `Cake-A-Pult Damage`, `Laboratory`. */
    val name: String,
    /** Shown after the arrow, e.g. `12`; `(new)` without an arrow for a building under construction. */
    val levelText: String?,
    val showArrow: Boolean,
    /** Icon files to try in order: `assets/icons/<id>.webp` (or `.png`). */
    val iconIds: List<Int>,
    /** Placeholder when no icon file exists, e.g. `IT`. */
    val initials: String,
    val finishAt: Long?,
    /** Wall-clock seconds until [finishAt] at build time. */
    val remaining: Long,
    /** 0..1, how much of the full upgrade is done. */
    val progress: Float,
    val button: RowButton,
    val goblin: Boolean = false,
    val jobKey: String? = null,
    /** Drawn when none of [iconIds] has an icon file (bundled or added by the player). */
    val fallbackIcon: FallbackIcon = FallbackIcon.BUILDING,
    /** The item's `data` ID when it has no real name; shown in the app's diagnostics, never on the widget. */
    val unknownId: Int? = null,
    /** Local finish day and time, `Thu 18:36`, for the "Finish time" display setting. */
    val finishClock: String? = null,
) {
    /** `Inferno Tower → 12`, `Super Wizard Tower (new)`. */
    val label: String
        get() = when {
            levelText == null -> name
            showArrow -> "$name → $levelText"
            else -> "$name $levelText"
        }

    /** Text when the per-second countdown is off (or for previews): `1d 20h`, `Completed`, `Idle`. */
    val staticStatus: String
        get() = when (kind) {
            RowKind.RUNNING -> WidgetModelBuilder.formatRemaining(remaining)
            RowKind.COMPLETED -> WidgetModelBuilder.COMPLETED
            RowKind.IDLE -> WidgetModelBuilder.IDLE
        }

    /**
     * Static status in a [style]: `1d 4h` (time left), `Thu 18:36` (finish time) or
     * `1d 4h · Thu 18:36` (both, no seconds). Finished and idle rows always say `Completed` / `Idle`.
     */
    fun statusText(style: CountdownStyle): String {
        if (kind != RowKind.RUNNING || finishClock == null) return staticStatus
        return when (style) {
            CountdownStyle.TIME_LEFT -> staticStatus
            CountdownStyle.FINISH_TIME -> finishClock
            CountdownStyle.BOTH -> "$staticStatus · $finishClock"
        }
    }
}

object WidgetModelBuilder {
    const val COMPLETED = "Completed"
    const val IDLE = "Idle"
    const val SYNC_FAILED = "Sync failed — copy data and tap Sync"
    const val NO_DATA = "Copy village data in Clash, then tap Sync"

    fun build(
        state: AppState,
        settings: AppSettings,
        names: NameMap,
        times: UpgradeTimes,
        now: Long,
        /** For clock times on the widget (helper `next 20:49`, finish times). */
        zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetModel {
        val export = state.export
        val pills = { type: BoostType ->
            BoostPill(type, boostLeft(state.boosts, type, now), state.boosts.any { it.type == type && it.end > now })
        }
        if (export == null) {
            return WidgetModel(
                hasData = false, playerName = settings.displayName, townHall = null,
                buildersBusy = 0, buildersTotal = settings.totalBuilders(null), goblinWorking = false,
                bite = pills(BoostType.BUILDER_BITE), builderPotion = pills(BoostType.BUILDER_POTION),
                researchPotion = pills(BoostType.RESEARCH_POTION), petPotion = pills(BoostType.PET_POTION),
                studySoup = pills(BoostType.STUDY_SOUP),
                builderRows = emptyList(),
                labRow = idleRow("Laboratory", LABORATORY), petRow = idleRow("Pet House", PET_HOUSE),
                footerLeft = if (state.syncFailed) listOf(FooterPart(SYNC_FAILED, FooterTone.ERROR)) else emptyList(),
                footerRight = "", syncFailed = state.syncFailed, nextChangeAt = null,
            )
        }

        val engine = TimerEngine(settings.boosts)
        val at = maxOf(now, export.timestamp) // the engine starts at T0
        val finish = export.jobs.associate { it.key to engine.finishTime(it, export, state.links, state.boosts) }
        fun running(job: Job) = !Finish.isDone(finish.getValue(job.key), now)

        fun row(job: Job): WidgetRow {
            val finishAt = finish.getValue(job.key)
            val done = Finish.isDone(finishAt, now)
            val workLeft = if (done) 0L else engine.remainingAt(job, export, state.links, state.boosts, at)
            val full = times.fullTime(job)?.takeIf { it >= job.remainingAtT0 }
                ?: maxOf(state.firstRemaining[job.key] ?: 0L, job.remainingAtT0)
            val title = names.title(job)
            val button = when {
                done -> RowButton.NONE
                job.key == state.links.apprenticeJobKey -> RowButton.APPRENTICE
                job.key == state.links.labAssistantJobKey -> RowButton.LAB_ASSISTANT
                job.category == JobCategory.PET -> RowButton.NONE
                // The Lab Assistant can't work for the Goblin Researcher.
                job.category == JobCategory.LAB && job.isGoblin -> RowButton.NONE
                else -> RowButton.LINK
            }
            return WidgetRow(
                kind = if (done) RowKind.COMPLETED else RowKind.RUNNING,
                name = title,
                levelText = if (job.isNewBuilding) "(new)" else job.targetLevel.toString(),
                showArrow = !job.isNewBuilding,
                iconIds = listOfNotNull(job.dataId, job.typeDataId, job.parentDataId).distinct(),
                initials = initials(title),
                finishAt = finishAt,
                remaining = if (done) 0 else finishAt - now,
                progress = if (done || full <= 0) 1f else (1.0 - workLeft.toDouble() / full).coerceIn(0.0, 1.0).toFloat(),
                button = button,
                goblin = job.isGoblin,
                jobKey = job.key,
                fallbackIcon = Fallbacks.icon(job.source.itemKind, names.isKnown(job)),
                unknownId = job.dataId.takeUnless { names.isKnown(job) },
                finishClock = FINISH_CLOCK.format(Instant.ofEpochSecond(finishAt).atZone(zone)),
            )
        }

        fun sorted(jobs: List<Job>) = jobs.sortedBy { finish.getValue(it.key) }

        // Free builders get no rows; finished jobs stay as "Completed" until the next sync.
        // The Goblin's row disappears once it is done (it is not one of the player's builders).
        val builderRows = sorted(export.builderJobs).filter { !it.isGoblin || running(it) }.map(::row)
        // The lab can run two researches at once during the Goblin Researcher event (issues #2, #4).
        val labRow = sorted(export.labJobs.filter { !it.isGoblin }).firstOrNull()?.let(::row)
            ?: idleRow("Laboratory", LABORATORY)
        val labGoblinRow = sorted(export.labJobs.filter { it.isGoblin && running(it) }).firstOrNull()?.let(::row)
        val petRow = sorted(export.petJobs).firstOrNull()?.let(::row) ?: idleRow("Pet House", PET_HOUSE)

        val helpers = HelperStatuses.of(state, settings.boosts, now)
        val footerLeft = if (state.syncFailed) {
            listOf(FooterPart(SYNC_FAILED, FooterTone.ERROR))
        } else {
            helpers.map { helperLine(it, names, now, zone) }
        }

        // Edges after which the model itself changes (countdown text edges are added by WidgetSchedule).
        val edges = buildList {
            // A row turns Completed GRACE_SEC before its finish; redraw again GRACE_SEC after it as a safety net.
            finish.values.forEach { add(it - Finish.GRACE_SEC); add(it + Finish.GRACE_SEC) }
            export.jobs.filter(::running).forEach { job ->
                engine.boostsFor(job, export, state.links, state.boosts).mapNotNull { it.nextEdgeAfter(at) }.forEach(::add)
            }
            state.boosts.filter { it.end > now }.forEach { add(if (it.start > now) it.start else it.end) }
            BoostType.entries.forEach { type -> boostLeft(state.boosts, type, now)?.let { add(now + pillEdgeIn(it)) } }
            add(export.timestamp + agoEdge(now - export.timestamp))
            // Helper lines count forward by themselves: next session passes, cooldown text, ready.
            helpers.forEach { h ->
                when (h) {
                    is HelperStatus.Working -> h.nextSession?.let(::add)
                    is HelperStatus.Cooldown -> { add(h.readyAt); HelperStatuses.cooldownTextEdge(h.readyAt, now)?.let(::add) }
                    is HelperStatus.Ready -> Unit
                }
            }
        }

        return WidgetModel(
            hasData = true,
            playerName = settings.displayName,
            townHall = export.townHallLevel,
            buildersBusy = export.regularBuilderJobs.count(::running),
            buildersTotal = settings.totalBuilders(export),
            goblinWorking = export.builderJobs.any { it.isGoblin && running(it) },
            bite = pills(BoostType.BUILDER_BITE),
            builderPotion = pills(BoostType.BUILDER_POTION),
            researchPotion = pills(BoostType.RESEARCH_POTION),
            petPotion = pills(BoostType.PET_POTION),
            studySoup = pills(BoostType.STUDY_SOUP),
            builderRows = builderRows,
            labRow = labRow,
            labGoblinRow = labGoblinRow,
            petRow = petRow,
            footerLeft = footerLeft,
            footerRight = "synced ${formatAgo(now - export.timestamp)}",
            syncFailed = state.syncFailed,
            nextChangeAt = edges.filter { it > now }.minOrNull(),
        )
    }

    const val LABORATORY = 1000007
    const val PET_HOUSE = 1000068

    internal fun idleRow(name: String, iconId: Int) = WidgetRow(
        kind = RowKind.IDLE, name = name, levelText = null, showArrow = false, iconIds = listOf(iconId),
        initials = initials(name), finishAt = null, remaining = 0, progress = 1f, button = RowButton.NONE,
    )

    /** Heroes by their title (`King`, `Champion`), everything else by its full name. */
    private fun shortName(job: Job, names: NameMap): String {
        val title = names.title(job)
        return if (job.source == JobSource.HERO && names.isKnown(job)) title.substringAfterLast(' ') else title
    }

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    /** English day names: the widget font only has Latin letters. */
    private val FINISH_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)

    /**
     * One footer line per helper (plan 1.1 §3.5), replacing 1.0's misleading "idle":
     * - working: `Apprentice: King · next 20:49` (next session start);
     * - on cooldown: `Apprentice: 14h` (time until it can be assigned again);
     * - ready: `Apprentice ready` + a drawn ✓ ([FooterPart.check]), in orange because it is unused.
     */
    fun helperLine(status: HelperStatus, names: NameMap, now: Long, zone: ZoneId): FooterPart {
        val who = status.kind.shortName
        return when (status) {
            is HelperStatus.Working -> {
                val next = status.nextSession?.let { " · next " + CLOCK.format(Instant.ofEpochSecond(it).atZone(zone)) } ?: ""
                FooterPart("$who: ${shortName(status.job, names)}$next")
            }
            is HelperStatus.Cooldown -> FooterPart("$who: ${HelperStatuses.formatCooldown(status.readyAt - now)}")
            is HelperStatus.Ready -> FooterPart("$who ready", FooterTone.IDLE, check = true)
        }
    }

    /** `IT` for Inferno Tower, `S` for Scattershot (first letters of the first two words). */
    fun initials(name: String): String =
        name.split(' ').mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
            .take(2).joinToString("").uppercase()

    /**
     * Seconds left on the running chain of [type] boosts at [now] (back-to-back windows count as one
     * run up to the end of the last one), or null if none is running.
     */
    fun boostLeft(boosts: List<BoostUse>, type: BoostType, now: Long): Long? {
        val windows = boosts.filter { it.type == type }.sortedBy { it.start }
        val current = windows.firstOrNull { now >= it.start && now < it.end } ?: return null
        var end = current.end
        for (w in windows) if (w.start <= end && w.end > end) end = w.end
        return end - now
    }

    /** Pill text: `42m`, `1h 50m`, `2h`. Minutes are rounded up so a running boost never shows `0m`. */
    fun formatPill(seconds: Long): String {
        val minutes = ceil(seconds / 60.0).toLong().coerceAtLeast(1)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Seconds until [formatPill] of a boost with [left] seconds changes. */
    private fun pillEdgeIn(left: Long): Long = left - 60 * ((left - 1) / 60)

    /** Seconds after T0 when [formatAgo] next changes, given [elapsed] seconds since T0. */
    private fun agoEdge(elapsed: Long): Long {
        val s = elapsed.coerceAtLeast(0)
        val unit = when {
            s < 3600 -> 60L
            s < 2 * 86400 -> 3600L
            else -> 86400L
        }
        return (s / unit + 1) * unit
    }

    /** `1d 20h`, `5h 12m`, `12m`. Minutes are rounded up so a running job never shows `0m`. */
    fun formatRemaining(seconds: Long): String {
        val minutes = (seconds + 59) / 60
        val d = minutes / (24 * 60)
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    /** `just now`, `12m ago`, `4h ago`, `3d ago`. */
    fun formatAgo(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60}m ago"
            s < 2 * 86400 -> "${s / 3600}h ago"
            else -> "${s / 86400}d ago"
        }
    }
}

/** Row sizes by widget height (plan 1.1 §5.5, issue #1). Nothing ever scrolls. */
enum class LayoutMode {
    /** 30 dp rows. */
    NORMAL,
    /** 25 dp rows. */
    COMPACT,
    /** One 25 dp row per section (next finish only), no section lines or footer. */
    SUMMARY,
}

object WidgetLayout {
    /**
     * Estimated panel height in dp with 30 dp rows (see widget_root.xml / widget_row.xml): padding 20,
     * header 26, three section lines with 40 dp buttons, two footer lines 34.
     */
    fun normalHeightDp(rows: Int): Int = 200 + 33 * rows

    /** The same with 25 dp rows (2 dp apart). */
    fun compactHeightDp(rows: Int): Int = 200 + 27 * rows

    /** [heightDp] = what the launcher reports; null (unknown) keeps the normal layout. */
    fun mode(heightDp: Int?, rows: Int): LayoutMode = when {
        heightDp == null || heightDp >= normalHeightDp(rows) -> LayoutMode.NORMAL
        heightDp >= compactHeightDp(rows) -> LayoutMode.COMPACT
        else -> LayoutMode.SUMMARY
    }
}

/** When the widget must be re-rendered next. */
object WidgetSchedule {
    /**
     * Epoch ms of the next re-render: the model's own next change, or the next countdown format edge
     * ([Countdown.segment]) with [liveCountdown], or the next change of the `1d 20h` text without it.
     */
    fun nextUpdateMs(
        model: WidgetModel,
        nowMs: Long,
        liveCountdown: Boolean,
        style: CountdownStyle = CountdownStyle.TIME_LEFT,
    ): Long? {
        val candidates = buildList {
            model.nextChangeAt?.let { add(it * 1000) }
            model.allRows.filter { it.kind == RowKind.RUNNING && it.finishAt != null }.forEach { row ->
                val finishAt = row.finishAt!!
                // The row turns Completed GRACE_SEC before the finish, so the chronometer never reaches zero.
                add((finishAt - Finish.GRACE_SEC) * 1000)
                when {
                    // `Thu 18:36` only changes when the finish itself moves (boost edges are in nextChangeAt).
                    style == CountdownStyle.FINISH_TIME -> Unit
                    style == CountdownStyle.TIME_LEFT && liveCountdown ->
                        Countdown.segment(finishAt, nowMs)?.let { add(it.nextEdgeMs) }
                    // `1d 20h` alone or in `1d 4h · Thu 18:36`.
                    else -> staticTextEdge(finishAt, nowMs / 1000)?.let { add(it * 1000) }
                }
            }
        }
        return candidates.filter { it > nowMs }.minOrNull()
    }

    /** Epoch s when [WidgetModelBuilder.formatRemaining] of a job finishing at [finishAt] next changes. */
    fun staticTextEdge(finishAt: Long, now: Long): Long? {
        val left = finishAt - now
        if (left <= 0) return null
        val minutes = (left + 59) / 60
        // Under a day the text shows minutes; from a day up it only shows whole hours.
        val nextMinutes = if (minutes > 24 * 60) 60 * (minutes / 60) - 1 else minutes - 1
        val edge = finishAt - nextMinutes * 60
        return if (edge > now) edge else null
    }
}
