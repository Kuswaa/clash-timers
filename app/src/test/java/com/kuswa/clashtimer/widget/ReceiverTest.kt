package com.kuswa.clashtimer.widget

import com.kuswa.clashtimer.core.BoostType
import com.kuswa.clashtimer.core.SampleExports
import com.kuswa.clashtimer.core.WidgetActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan 1.1 §3.3 / §3.4: broadcast finishing (issue #7) and unique tap intents. */
class ReceiverTest {

    @Test fun `finish called twice does not throw and finishes once`() {
        var calls = 0
        // Like PendingResult.finish(): the second real call throws "Broadcast already finished".
        val f = OnceFinisher { calls++; if (calls > 1) throw IllegalStateException("Broadcast already finished") }
        assertTrue(f.finish())
        assertFalse(f.finish())
        assertFalse(f.finish())
        assertEquals(1, calls)
        assertTrue(f.isFinished)
    }

    @Test fun `finish after Android already finished the broadcast does not throw`() {
        val f = OnceFinisher { throw IllegalStateException("Broadcast already finished") }
        assertFalse(f.finish())
        assertFalse(f.finish())
    }

    @Test fun `concurrent finishes call the real finish once`() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val f = OnceFinisher { calls.incrementAndGet() }
        val threads = (1..8).map { Thread { repeat(100) { f.finish() } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(1, calls.get())
    }

    @Test fun `every widget button gets its own request code`() {
        val alt = SampleExports.alt1
        val taps = alt.jobs.map { WidgetActions.HELPER to it.key } +
            BoostType.entries.flatMap { listOf(WidgetActions.ADD_BOOST to it.name, WidgetActions.UNDO_BOOST to it.name) }
        val codes = taps.map { (a, b) -> TapCodes.requestCode(a, b) }
        assertEquals(taps.size, codes.toSet().size)
        assertTrue(codes.all { it >= 1000 }) // clear of the alarms (1, 2) and the Sync intent (0)
        assertEquals(codes, taps.map { (a, b) -> TapCodes.requestCode(a, b) }) // stable between renders
    }
}
