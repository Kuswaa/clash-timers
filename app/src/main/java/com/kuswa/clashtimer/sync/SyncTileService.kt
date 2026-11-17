package com.kuswa.clashtimer.sync

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile "Clash Sync" (plan 1.1 §5.3): lets the player sync from inside the game.
 * Copy the village data in Clash, pull down the notification shade, tap the tile. It opens the same
 * invisible [SyncActivity] as the widget's Sync button, which reads the clipboard once it has focus,
 * syncs and closes.
 */
class SyncTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.run {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // On the lock screen, unlock first: the clipboard and the app's data need an unlocked phone.
        if (isLocked) unlockAndRun { openSync() } else openSync()
    }

    private fun openSync() {
        val intent = SyncActivity.intent(this, SyncActivity.SOURCE_TILE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: only the PendingIntent variant may start an activity from a tile.
                val pi = PendingIntent.getActivity(
                    this, REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: RuntimeException) {
            // Some ROMs refuse; never crash the System UI's tile host over it.
        }
    }

    private companion object {
        /** Away from the widget's Sync intent (0), the alarms (1, 2) and the tap codes (≥ 1000). */
        const val REQUEST_CODE = 3
    }
}
