@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kuswa.clashtimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kuswa.clashtimer.R
import com.kuswa.clashtimer.core.AppSettings
import com.kuswa.clashtimer.core.AppState
import com.kuswa.clashtimer.core.SyncLog
import com.kuswa.clashtimer.core.WidgetModel

private enum class Destination(val label: String, val icon: Int) {
    TIMERS("Timers", R.drawable.ic_timers),
    SETTINGS("Settings", R.drawable.ic_settings),
    SUPPORT("Support", R.drawable.ic_support),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashTimersContent(
    state: AppState,
    settings: AppSettings,
    model: WidgetModel,
    log: SyncLog,
    now: Long,
    timesCount: Int,
    busy: Boolean,
    onSync: () -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TIMERS) }
    val screenState = rememberSaveableStateHolder()
    BackHandler(enabled = destination != Destination.TIMERS) { destination = Destination.TIMERS }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    if (destination == Destination.TIMERS) "Clash Timers" else destination.label,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            })
        },
        bottomBar = {
            ShortNavigationBar(arrangement = ShortNavigationBarArrangement.EqualWeight) {
                Destination.entries.forEach { entry ->
                    ShortNavigationBarItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                screenState.SaveableStateProvider(destination) {
                    when (destination) {
                        Destination.TIMERS -> TimerDashboard(model, log.lastResult, busy, onSync)
                        Destination.SETTINGS -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item { SettingsSection(settings, state.export, now, onSettingsChange) }
                        }
                        Destination.SUPPORT -> SupportScreen(state, model, log, timesCount, now)
                    }
                }
            }
        }
    }
}
