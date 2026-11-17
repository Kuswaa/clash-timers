package com.kuswa.clashtimer.widget

/**
 * Request codes for the widget's tap PendingIntents. Each (action, argument) pair gets its own code,
 * away from the alarm codes (1, 2) and the Sync intent (0). Plain Kotlin, unit tested.
 */
object TapCodes {
    private const val BASE = 1_000

    fun requestCode(action: String, arg: String): Int {
        // Stable across renders (String.hashCode is specified), never 0..BASE-1.
        val h = "$action/$arg".hashCode() and 0x3FFFFFFF
        return BASE + h % (Int.MAX_VALUE / 2 - BASE)
    }
}
