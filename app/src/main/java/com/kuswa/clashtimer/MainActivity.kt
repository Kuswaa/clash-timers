@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kuswa.clashtimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kuswa.clashtimer.core.AppSettings
import com.kuswa.clashtimer.core.AppState
import com.kuswa.clashtimer.core.SyncLog
import com.kuswa.clashtimer.core.WidgetModelBuilder
import com.kuswa.clashtimer.data.Names
import com.kuswa.clashtimer.data.StateRepository
import com.kuswa.clashtimer.data.Times
import com.kuswa.clashtimer.sync.SyncRunner
import com.kuswa.clashtimer.ui.ClashTimersContent
import com.kuswa.clashtimer.ui.PlayerNameDialog
import com.kuswa.clashtimer.ui.theme.ClashTimerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClashTimerTheme { ClashTimersApp() } }
    }
}

private data class ScreenData(val state: AppState, val settings: AppSettings, val log: SyncLog)
private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: ScreenData) : LoadState
    data class Failed(val message: String) : LoadState
}

@Composable
private fun ClashTimersApp() {
    val context = LocalContext.current
    val repo = remember { StateRepository(context) }
    val names = remember { Names.get(context) }
    val times = remember { Times.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var retry by remember { mutableIntStateOf(0) }
    val dataFlow = remember(repo, retry) {
        combine(repo.state, repo.settings, repo.syncLog, ::ScreenData)
            .map<ScreenData, LoadState> { LoadState.Ready(it) }
            .catch { emit(LoadState.Failed(it.message ?: "Could not read saved village data.")) }
    }
    val loadState by dataFlow.collectAsStateWithLifecycle(initialValue = LoadState.Loading)
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis() / 1000
                delay(1_000)
            }
        }
    }
    var busy by remember { mutableStateOf(false) }

    when (val loaded = loadState) {
        LoadState.Loading -> Surface {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoadingIndicator()
                Text("Loading your village…", Modifier.padding(top = 16.dp))
            }
        }
        is LoadState.Failed -> Surface {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("Could not load your village", style = MaterialTheme.typography.titleLarge)
                Text(loaded.message)
                Button(onClick = { retry++ }) { Text("Try again") }
            }
        }
        is LoadState.Ready -> {
            val (state, settings, log) = loaded.data
            val model = remember(state, settings, now) { WidgetModelBuilder.build(state, settings, names, times, now) }
            val changeSettings: ((AppSettings) -> AppSettings) -> Unit = { change ->
                scope.launch {
                    try {
                        repo.updateSettings(change)
                        SyncRunner.refreshWidgets(context, "app")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Could not finish updating settings: ${e.message}")
                    }
                }
            }
            if (settings.needsPlayerName) {
                val answer = { name: String -> changeSettings { it.copy(playerName = name) } }
                PlayerNameDialog(null, true, onDone = answer, onDismiss = { answer("") })
            }
            ClashTimersContent(
                state = state,
                settings = settings,
                model = model,
                log = log,
                now = now,
                timesCount = times.size,
                busy = busy,
                onSync = {
                    if (!busy) {
                        busy = true
                        // Explicit user action while the activity has focus. Never read on launch.
                        val clipboard = SyncRunner.readClipboard(context)
                        scope.launch {
                            try {
                                SyncRunner.sync(context, clipboard, "app")
                                now = System.currentTimeMillis() / 1000
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not finish syncing: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                onSettingsChange = changeSettings,
                snackbarHost = snackbar,
            )
        }
    }
}
