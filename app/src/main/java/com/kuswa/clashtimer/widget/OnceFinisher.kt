package com.kuswa.clashtimer.widget

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps `BroadcastReceiver.PendingResult.finish()` (issue #7: `IllegalStateException: Broadcast already
 * finished` on a vivo phone, Android 12). Android may already have finished a slow broadcast itself after
 * its ~10 s limit, and both the work and the receiver's watchdog want to finish it. So [finish] runs the
 * real call at most once and never throws. Plain Kotlin, so it is unit tested.
 */
class OnceFinisher(private val realFinish: () -> Unit) {
    private val done = AtomicBoolean(false)

    val isFinished: Boolean get() = done.get()

    /** True if this call finished the broadcast; false if it was already finished (by us or by Android). */
    fun finish(): Boolean {
        if (!done.compareAndSet(false, true)) return false
        return try {
            realFinish()
            true
        } catch (e: IllegalStateException) {
            false // "Broadcast already finished": Android gave up on it first
        } catch (e: RuntimeException) {
            false // never let finishing a broadcast crash the app
        }
    }
}
