package com.kuswa.clashtimer.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.kuswa.clashtimer.core.AccountNotices
import com.kuswa.clashtimer.core.WidgetActions
import com.kuswa.clashtimer.core.WidgetModelBuilder
import com.kuswa.clashtimer.core.WidgetSchedule
import com.kuswa.clashtimer.data.Names
import com.kuswa.clashtimer.data.StateRepository
import com.kuswa.clashtimer.data.Times
import com.kuswa.clashtimer.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The home-screen widget, drawn with plain RemoteViews (not Glance) so it can use the Lilita One
 * font, Chronometer countdowns, progress bars and the design's drawables.
 * The class name is unchanged from the Glance version so a placed widget keeps working.
 *
 * Also the app's single broadcast entry point: widget button taps (step 5), the redraw and
 * finish alarms, and boot / app update / clock change, which all end in [WidgetUpdater.updateAll].
 */
class ClashWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, "widget")

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context, "widget")

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> refresh(context, "widget")
            ACTION_FINISH -> refresh(context, "alarm")
            ACTION_TAP -> tap(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, ACTION_QUICKBOOT,
            -> refresh(context, "system")
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) = WidgetAlarm.cancelRedraw(context)

    private fun refresh(context: Context, source: String) = runAsync(context) { WidgetUpdater.updateAll(context, source) }

    /** A widget button: `clashtimer://tap/<action>/<arg>`. Saved, logged, then redrawn at once. */
    private fun tap(context: Context, intent: Intent) {
        val segments = intent.data?.pathSegments.orEmpty()
        val action = segments.getOrNull(0) ?: return
        val arg = segments.getOrNull(1) ?: return
        runAsync(context) {
            val app = context.applicationContext
            val repo = StateRepository(app)
            try {
                val settings = repo.currentSettings()
                val names = Names.load(app)
                val now = System.currentTimeMillis() / 1000
                val line = repo.updateState { before ->
                    val after = WidgetActions.apply(before, action, arg, now, settings.boosts)
                    after to WidgetActions.describe(before, after, action, arg, names)
                }
                repo.log("tap", null, line)
            } catch (t: Throwable) {
                repo.log("tap", false, "Tap failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            WidgetUpdater.updateAll(app, "tap")
        }
    }

    /**
     * Runs [block] off the main thread while holding the broadcast, but never longer than
     * [RECEIVER_BUDGET_MS] (Android kills or force-finishes broadcasts after ~10 s, issue #7). If the work
     * is slower, the broadcast is finished anyway and the work carries on in the app's scope; the
     * [OnceFinisher] makes sure the second finish() (or one after Android's own) can't throw.
     */
    private fun runAsync(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        val finisher = OnceFinisher { pending.finish() }
        val app = context.applicationContext
        val work = scope.launch {
            try {
                block()
            } finally {
                finisher.finish()
            }
        }
        val watchdog = scope.launch {
            delay(RECEIVER_BUDGET_MS)
            if (finisher.finish()) {
                StateRepository(app).log("widget", null, "Slow refresh: released the broadcast after ${RECEIVER_BUDGET_MS / 1000} s, finishing in the background")
            }
        }
        work.invokeOnCompletion { watchdog.cancel() }
    }

    companion object {
        const val ACTION_TICK = "com.kuswa.clashtimer.WIDGET_TICK"
        const val ACTION_FINISH = "com.kuswa.clashtimer.JOB_FINISH"
        const val ACTION_TAP = "com.kuswa.clashtimer.WIDGET_TAP"
        /** Some ROMs send this instead of BOOT_COMPLETED after a fast reboot. */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        /** Well under Android's ~10 s broadcast limit. */
        const val RECEIVER_BUDGET_MS = 6_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

object WidgetUpdater {

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClashWidgetReceiver::class.java))

    /** One refresh at a time: taps, alarms and syncs arriving together queue up instead of racing. */
    private val lock = Mutex()

    /**
     * Everything that follows a change or an alarm: re-renders every placed widget (first, so a tap
     * shows at once), posts "… free" / "… is ready" notifications, and schedules the redraw alarm and
     * the finish alarm. Returns the number of widgets, or -1 on failure (logged to the on-screen sync
     * log; never throws).
     */
    suspend fun updateAll(context: Context, source: String): Int = lock.withLock { doUpdate(context, source) }

    private suspend fun doUpdate(context: Context, source: String): Int {
        val app = context.applicationContext
        val repo = StateRepository(app)
        return try {
            val settings = repo.currentSettings()
            val names = Names.load(app)
            val nowMs = System.currentTimeMillis()
            val now = nowMs / 1000

            val manager = AppWidgetManager.getInstance(app)
            val ids = widgetIds(app)
            if (ids.isEmpty()) {
                WidgetAlarm.cancelRedraw(app)
            } else {
                val model = WidgetModelBuilder.build(repo.currentState(), settings, names, Times.get(app), now)
                val renderer = WidgetRenderer(app)
                for (id in ids) {
                    manager.updateAppWidget(
                        id, renderer.render(model, settings.liveCountdown, manager.getAppWidgetOptions(id), settings.countdownStyle),
                    )
                }
                WidgetAlarm.scheduleRedraw(app, WidgetSchedule.nextUpdateMs(model, nowMs, settings.liveCountdown, settings.countdownStyle))
            }

            // Notifications for every saved account (timers keep running for the one not shown), marked as
            // sent in the same atomic update so two alarms can't double-post.
            // Own try: a notification failure must never skip the finish alarm below.
            try {
                val notices = repo.updateAccounts { accounts -> AccountNotices.check(accounts, settings, names, now) }
                if (notices.isNotEmpty()) {
                    if (settings.notificationsEnabled) {
                        val posted = Notifier.post(app, notices)
                        repo.log(source, null, if (posted) "Notified: " + notices.joinToString("; ") { it.title }
                            else "Not notified (notifications are blocked for the app): " + notices.joinToString("; ") { it.title })
                    } else {
                        repo.log(source, null, "Finished (notifications off): " + notices.joinToString("; ") { it.title })
                    }
                }
            } catch (t: Throwable) {
                repo.log(source, false, "Notification failed: ${t.javaClass.simpleName}: ${t.message}")
            }

            // Finish + GRACE_SEC, so the redraw it triggers sees the job as done. Earliest over all accounts.
            WidgetAlarm.scheduleFinish(app, AccountNotices.alarmAt(repo.currentAccounts(), settings, now)?.times(1000))
            ids.size
        } catch (t: Throwable) {
            repo.log(source, false, "Widget refresh failed: ${t.javaClass.simpleName}: ${t.message}")
            -1
        }
    }
}

/**
 * Two pending alarms:
 * - **redraw** (non-wakeup `RTC`): countdown edges, boost edges, footer text. If the phone is asleep
 *   it is delivered when the screen comes back on, which is when the widget is visible anyway.
 * - **finish** (wakeup, allowed while idle): the next time any job finishes, so the notification
 *   arrives on time even with the screen off.
 */
object WidgetAlarm {
    /** Fire slightly after an edge so the re-render lands in the next countdown segment. */
    private const val SLACK_MS = 250L
    private const val MIN_DELAY_MS = 1_000L

    private fun intent(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, ClashWidgetReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun scheduleRedraw(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ClashWidgetReceiver.ACTION_TICK, 1)
        if (atMs == null) return am.cancel(pi)
        val at = maxOf(atMs + SLACK_MS, System.currentTimeMillis() + MIN_DELAY_MS)
        try {
            if (canScheduleExact(context)) am.setExact(AlarmManager.RTC, at, pi)
            else am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
        }
    }

    fun scheduleFinish(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ClashWidgetReceiver.ACTION_FINISH, 2)
        if (atMs == null) return am.cancel(pi)
        val at = maxOf(atMs + SLACK_MS, System.currentTimeMillis() + MIN_DELAY_MS)
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelRedraw(context: Context) = scheduleRedraw(context, null)
}
