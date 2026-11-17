package com.kuswa.clashtimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.kuswa.clashtimer.R
import com.kuswa.clashtimer.core.BoostPill
import com.kuswa.clashtimer.core.WidgetActions
import com.kuswa.clashtimer.core.Countdown
import com.kuswa.clashtimer.core.Finish
import com.kuswa.clashtimer.core.FooterPart
import com.kuswa.clashtimer.core.FooterTone
import com.kuswa.clashtimer.core.RowButton
import com.kuswa.clashtimer.core.RowKind
import com.kuswa.clashtimer.core.WidgetModel
import com.kuswa.clashtimer.core.WidgetModelBuilder
import com.kuswa.clashtimer.core.WidgetRow
import com.kuswa.clashtimer.core.CountdownStyle
import com.kuswa.clashtimer.core.FallbackIcon
import com.kuswa.clashtimer.core.LayoutMode
import com.kuswa.clashtimer.core.WidgetLayout
import com.kuswa.clashtimer.sync.SyncActivity
import java.io.File
import kotlin.math.roundToInt

/** Maps a [WidgetModel] onto `widget_root.xml` + one `widget_row*.xml` per row. */
class WidgetRenderer(private val context: Context) {

    private val pkg = context.packageName
    private val icons = WidgetIcons.get(context)
    private val density = context.resources.displayMetrics.density

    fun render(
        model: WidgetModel,
        liveCountdown: Boolean,
        options: Bundle?,
        style: CountdownStyle = CountdownStyle.TIME_LEFT,
    ): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root)
        val sync = syncIntent()
        rv.setOnClickPendingIntent(R.id.header, sync)
        rv.setOnClickPendingIntent(R.id.sync_button, sync)
        rv.setTextViewText(R.id.who, title(model))

        if (!model.hasData) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, sync)
            rv.setTextViewText(R.id.empty_text, if (model.syncFailed) WidgetModelBuilder.SYNC_FAILED else WidgetModelBuilder.NO_DATA)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)

        rv.setTextViewText(R.id.builders_label, buildersLabel(model))
        pill(rv, PillIds(R.id.pill_bite, R.id.pill_bite_add, R.id.pill_bite_undo, R.id.pill_bite_icon, R.id.pill_bite_text),
            model.bite, "builder_bite", R.drawable.ic_builder_bite)
        pill(rv, PillIds(R.id.pill_bpot, R.id.pill_bpot_add, R.id.pill_bpot_undo, R.id.pill_bpot_icon, R.id.pill_bpot_text),
            model.builderPotion, "builder_potion", R.drawable.ic_builder_potion)
        pill(rv, PillIds(R.id.pill_rpot, R.id.pill_rpot_add, R.id.pill_rpot_undo, R.id.pill_rpot_icon, R.id.pill_rpot_text),
            model.researchPotion, "research_potion", R.drawable.ic_research_potion)
        pill(rv, PillIds(R.id.pill_ppot, R.id.pill_ppot_add, R.id.pill_ppot_undo, R.id.pill_ppot_icon, R.id.pill_ppot_text),
            model.petPotion, "pet_potion", R.drawable.ic_pet_potion)
        // One Study Soup button, next to the research potion; its window speeds up the lab and the pets.
        pill(rv, PillIds(R.id.pill_lsoup, R.id.pill_lsoup_add, R.id.pill_lsoup_undo, R.id.pill_lsoup_icon, R.id.pill_lsoup_text),
            model.studySoup, "study_soup", R.drawable.ic_study_soup)

        val mode = layoutMode(options, model.allRows.size)
        val compact = mode != LayoutMode.NORMAL
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        fun fill(@IdRes container: Int, rows: List<WidgetRow>) {
            rv.removeAllViews(container)
            rows.take(MAX_BUILDER_ROWS).forEach { rv.addView(container, row(it, compact, liveCountdown, style, nowMs, elapsed)) }
        }
        // Very small widget (issue #1): one line per section, the next finish only; no section lines,
        // no footer, nothing to scroll.
        val summary = mode == LayoutMode.SUMMARY
        val sections = if (summary) View.GONE else View.VISIBLE
        rv.setViewVisibility(R.id.line_builders, sections)
        rv.setViewVisibility(R.id.line_lab, sections)
        rv.setViewVisibility(R.id.line_pets, sections)
        rv.setViewVisibility(R.id.footer, sections)
        if (summary) {
            val (b, l, p) = model.summaryRows
            fill(R.id.rows_builders, listOf(b))
            fill(R.id.rows_lab, listOf(l))
            fill(R.id.rows_pet, listOf(p))
        } else {
            fill(R.id.rows_builders, model.builderRows)
            fill(R.id.rows_lab, model.labRows)
            fill(R.id.rows_pet, listOf(model.petRow))
        }

        footer(rv, model.footerLeft)
        rv.setTextViewText(R.id.foot_right, model.footerRight)
        return rv
    }

    /** Up to two footer lines (one per helper), each with an optional drawn ✓. */
    private fun footer(rv: RemoteViews, lines: List<FooterPart>) {
        fun line(@IdRes text: Int, @IdRes check: Int, part: FooterPart?) {
            rv.setTextViewText(text, part?.text ?: "")
            rv.setTextColor(text, when (part?.tone) {
                FooterTone.IDLE -> COLOR_IDLE
                FooterTone.ERROR -> COLOR_ERROR
                else -> COLOR_SUB
            })
            rv.setViewVisibility(check, if (part?.check == true) View.VISIBLE else View.GONE)
        }
        line(R.id.foot_line1, R.id.foot_check1, lines.getOrNull(0))
        line(R.id.foot_line2, R.id.foot_check2, lines.getOrNull(1))
        rv.setViewVisibility(R.id.foot_row2, if (lines.size > 1) View.VISIBLE else View.GONE)
    }

    private fun row(
        model: WidgetRow,
        compact: Boolean,
        live: Boolean,
        style: CountdownStyle,
        nowMs: Long,
        elapsed: Long,
    ): RemoteViews {
        // The model already marks a row done Finish.GRACE_SEC early. This also covers a model built a
        // moment before this render, so a chronometer is never started at or past its finish.
        val row = if (model.kind == RowKind.RUNNING && model.finishAt != null && Finish.isDone(model.finishAt, nowMs / 1000)) {
            model.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            model
        }
        val v =RemoteViews(pkg, if (compact) R.layout.widget_row_compact else R.layout.widget_row)
        val iconPx = ((if (compact) 20 else 24) * density).roundToInt()

        val icon = row.iconIds.firstNotNullOfOrNull { icons.bitmap(it.toString(), iconPx) }
        if (icon != null) {
            v.setImageViewBitmap(R.id.row_icon, icon)
        } else {
            // No icon file (e.g. a new TH19 item): a generic category icon, not initials (plan 1.1 §4).
            v.setImageViewResource(R.id.row_icon, fallbackDrawable(row.fallbackIcon))
        }
        v.setViewVisibility(R.id.row_icon, View.VISIBLE)
        v.setViewVisibility(R.id.row_initials, View.GONE)

        if (row.goblin) {
            v.setViewVisibility(R.id.row_goblin, View.VISIBLE)
            icons.bitmap("goblin", (11 * density).roundToInt())?.let {
                v.setImageViewBitmap(R.id.row_goblin_icon, it)
                v.setViewVisibility(R.id.row_goblin_icon, View.VISIBLE)
            }
        }
        v.setTextViewText(R.id.row_name, row.name)
        // Finished rows show just the name (e.g. "Inferno Tower   Completed ✓").
        val level = row.levelText?.takeIf { row.kind == RowKind.RUNNING }
        v.setViewVisibility(R.id.row_arrow, if (level != null && row.showArrow) View.VISIBLE else View.GONE)
        if (level != null) v.setTextViewText(R.id.row_level, level)
        else v.setViewVisibility(R.id.row_level, View.GONE)

        when (row.kind) {
            RowKind.RUNNING -> {
                v.setProgressBar(R.id.row_fill, 1000, (row.progress * 1000).roundToInt(), false)
                icons.bitmap("clock", (12 * density).roundToInt())?.let { v.setImageViewBitmap(R.id.row_clock, it) }
                // Ticking seconds only for "Time left"; finish times are static text (no seconds).
                val ticking = live && style == CountdownStyle.TIME_LEFT
                val segment = if (ticking) row.finishAt?.let { Countdown.segment(it, nowMs) } else null
                if (segment != null) {
                    v.setChronometer(R.id.row_chrono, elapsed + segment.chronometerMs, segment.format, true)
                    v.setChronometerCountDown(R.id.row_chrono, true)
                } else {
                    v.setViewVisibility(R.id.row_chrono, View.GONE)
                    v.setViewVisibility(R.id.row_text, View.VISIBLE)
                    v.setTextViewText(R.id.row_text, row.statusText(style))
                }
            }
            RowKind.COMPLETED, RowKind.IDLE -> {
                v.setViewVisibility(R.id.row_fill, View.GONE)
                v.setInt(R.id.row, "setBackgroundResource", R.drawable.row_done)
                v.setViewVisibility(R.id.row_clock, View.GONE)
                v.setViewVisibility(R.id.row_chrono, View.GONE)
                v.setViewVisibility(R.id.row_text, View.VISIBLE)
                v.setTextViewText(R.id.row_text, row.staticStatus)
                if (row.kind == RowKind.COMPLETED) v.setViewVisibility(R.id.row_check, View.VISIBLE)
            }
        }

        if (row.button != RowButton.NONE && row.jobKey != null) {
            v.setOnClickPendingIntent(R.id.row_btn, tapIntent(WidgetActions.HELPER, row.jobKey))
        }
        when (row.button) {
            RowButton.NONE -> v.setViewVisibility(R.id.row_btn, View.INVISIBLE)
            RowButton.LINK -> Unit // green + is the layout default
            RowButton.APPRENTICE, RowButton.LAB_ASSISTANT -> {
                v.setInt(R.id.row_btn, "setBackgroundResource", R.drawable.btn_helper)
                val name = if (row.button == RowButton.APPRENTICE) "apprentice" else "lab_assistant"
                val badge = icons.bitmap(name, ((if (compact) 19 else 22) * density).roundToInt())
                if (badge != null) {
                    v.setImageViewBitmap(R.id.row_btn_icon, badge)
                    v.setViewVisibility(R.id.row_btn_icon, View.VISIBLE)
                    v.setViewVisibility(R.id.row_btn_text, View.GONE)
                } else {
                    v.setTextViewText(R.id.row_btn_text, "H")
                    v.setTextColor(R.id.row_btn_text, COLOR_SYNC_TEXT)
                    v.setTextViewTextSize(R.id.row_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, 10f)
                }
            }
        }
        return v
    }

    @DrawableRes
    private fun fallbackDrawable(icon: FallbackIcon): Int = when (icon) {
        FallbackIcon.BUILDING -> R.drawable.ic_cat_building
        FallbackIcon.TROOP -> R.drawable.ic_cat_troop
        FallbackIcon.SQUAD -> R.drawable.ic_cat_squad
        FallbackIcon.SPELL -> R.drawable.ic_cat_spell
        FallbackIcon.PET -> R.drawable.ic_cat_pet
    }

    private class PillIds(
        @param:IdRes val box: Int, @param:IdRes val add: Int, @param:IdRes val undo: Int,
        @param:IdRes val icon: Int, @param:IdRes val text: Int,
    )

    private fun pill(rv: RemoteViews, ids: PillIds, pill: BoostPill, asset: String, @DrawableRes fallback: Int) {
        val bmp = icons.bitmap(asset, (14 * density).roundToInt())
        if (bmp != null) rv.setImageViewBitmap(ids.icon, bmp) else rv.setImageViewResource(ids.icon, fallback)
        rv.setTextViewText(ids.text, pill.text)
        rv.setTextColor(ids.text, if (pill.active) COLOR_GOLD else COLOR_WHITE)
        rv.setInt(ids.box, "setBackgroundResource", if (pill.active || pill.canUndo) R.drawable.pill_on else R.drawable.pill_off)
        rv.setOnClickPendingIntent(ids.add, tapIntent(WidgetActions.ADD_BOOST, pill.type.name))
        rv.setViewVisibility(ids.undo, if (pill.canUndo) View.VISIBLE else View.GONE)
        if (pill.canUndo) rv.setOnClickPendingIntent(ids.undo, tapIntent(WidgetActions.UNDO_BOOST, pill.type.name))
    }

    /**
     * Broadcast to [ClashWidgetReceiver] for a button tap, handled in the background (no activity, no popup).
     * Every button gets its own PendingIntent: a distinct URI (action + argument) and a distinct request
     * code, so no two buttons or rows can ever share or overwrite each other's intent (plan 1.1 §3.4).
     */
    private fun tapIntent(action: String, arg: String): PendingIntent {
        val uri = Uri.Builder().scheme("clashtimer").authority("tap").appendPath(action).appendPath(arg).build()
        val intent = Intent(context, ClashWidgetReceiver::class.java)
            .setAction(ClashWidgetReceiver.ACTION_TAP)
            .setData(uri)
        return PendingIntent.getBroadcast(
            context, TapCodes.requestCode(action, arg), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Player name + small gold `TH18`. */
    private fun title(model: WidgetModel): CharSequence {
        val sb = SpannableStringBuilder(model.playerName)
        model.townHall?.let { th ->
            val start = sb.length
            sb.append(" TH$th")
            sb.setSpan(AbsoluteSizeSpan(12, true), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(COLOR_GOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /** `BUILDERS 6/6` + green ` + Goblin`. */
    private fun buildersLabel(model: WidgetModel): CharSequence {
        val sb = SpannableStringBuilder(model.buildersLabel)
        if (model.goblinWorking) {
            val start = sb.length
            sb.append(" + Goblin")
            sb.setSpan(ForegroundColorSpan(COLOR_GOBLIN), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun syncIntent(): PendingIntent {
        val intent = SyncActivity.intent(context, SyncActivity.SOURCE_WIDGET)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val MAX_BUILDER_ROWS = 8

        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private const val COLOR_GOLD = 0xFFFFD24A.toInt()
        private const val COLOR_GOBLIN = 0xFF6FD36A.toInt()
        private const val COLOR_IDLE = 0xFFFFB35C.toInt()
        private const val COLOR_SUB = 0xFFE9DCC8.toInt()
        private const val COLOR_ERROR = 0xFFFF7A7A.toInt()
        private const val COLOR_SYNC_TEXT = 0xFF5A3500.toInt()

        /**
         * Estimated panel height in dp with 30 dp rows (see widget_root.xml / widget_row.xml): padding 20,
         * header 26, three section lines with 40 dp buttons, two footer lines 34.
         */
        fun normalHeightDp(rows: Int): Int = WidgetLayout.normalHeightDp(rows)

        /** Widget height in dp as reported by the launcher (portrait), or null if unknown. */
        fun reportedHeightDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 }

        fun reportedWidthDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 }

        /** Row size for a widget of the reported height (normal when the launcher doesn't say). */
        fun layoutMode(options: Bundle?, rows: Int): LayoutMode = WidgetLayout.mode(reportedHeightDp(options), rows)

        /** 25 dp rows (or the one-line layout) when the launcher says 30 dp rows won't fit. */
        fun useCompactRows(options: Bundle?, rows: Int): Boolean = layoutMode(options, rows) != LayoutMode.NORMAL
    }
}

/**
 * Icons from `assets/icons/` (64×64 WebP made by tools/gen_icons.cjs; see tools/icon_checklist.txt),
 * scaled to their on-screen size and cached. A hand-added `<name>.png` works too.
 */
class WidgetIcons private constructor(private val context: Context) {

    /** File names present in `assets/icons/`. */
    val available: Set<String> by lazy {
        runCatching { context.assets.list("icons")?.toSet() }.getOrNull() ?: emptySet()
    }

    private val cache = LruCache<String, Bitmap>(64)

    /** Icons the player picked on the "Unknown items" screen: `files/user_icons/<id>.webp`. */
    private val userDir: File get() = File(context.filesDir, USER_DIR)

    fun hasBundled(name: String): Boolean = EXTENSIONS.any { "$name.$it" in available }

    fun userIcon(name: String): File? = File(userDir, "$name.webp").takeIf { it.isFile }

    /**
     * The player's icon for [name] if there is one (it beats the bundled one), else
     * `assets/icons/<name>.webp` (or `.png`), scaled to [sizePx]; null if there is neither.
     */
    fun bitmap(name: String, sizePx: Int): Bitmap? {
        val user = userIcon(name)
        val file = if (user != null) null else EXTENSIONS.map { "$name.$it" }.firstOrNull { it in available }
        if (user == null && file == null) return null
        val key = "${user?.path ?: file}@$sizePx"
        cache.get(key)?.let { return it }
        val decoded = runCatching {
            if (user != null) BitmapFactory.decodeFile(user.path)
            else context.assets.open("icons/$file").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val scaled = if (decoded.width == sizePx && decoded.height == sizePx) decoded else {
            val scale = sizePx.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        cache.put(key, scaled)
        return scaled
    }

    /**
     * Saves [source] as the player's icon for [id]: centre-cropped to a square, scaled to 64×64 and
     * stored as WebP in app storage (no storage permission, nothing leaves the phone).
     */
    fun saveUserIcon(id: Int, source: Bitmap): Boolean {
        val side = minOf(source.width, source.height)
        if (side <= 0) return false
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val icon = Bitmap.createScaledBitmap(square, USER_ICON_PX, USER_ICON_PX, true)
        userDir.mkdirs()
        val tmp = File(userDir, "$id.webp.tmp")
        val ok = runCatching {
            tmp.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS
                else Bitmap.CompressFormat.WEBP
                icon.compress(format, 100, out)
            }
        }.getOrDefault(false) && tmp.renameTo(File(userDir, "$id.webp"))
        if (!ok) tmp.delete()
        cache.evictAll()
        return ok
    }

    fun deleteUserIcon(id: Int) {
        File(userDir, "$id.webp").delete()
        cache.evictAll()
    }

    companion object {
        private val EXTENSIONS = listOf("webp", "png")
        private const val USER_DIR = "user_icons"
        const val USER_ICON_PX = 64

        @Volatile private var instance: WidgetIcons? = null

        fun get(context: Context): WidgetIcons = instance ?: synchronized(this) {
            instance ?: WidgetIcons(context.applicationContext).also { instance = it }
        }
    }
}
