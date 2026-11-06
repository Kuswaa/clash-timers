package com.kuswa.clashtimer.ui

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kuswa.clashtimer.core.AppState
import com.kuswa.clashtimer.core.Countdown
import com.kuswa.clashtimer.core.RowButton
import com.kuswa.clashtimer.core.RowKind
import com.kuswa.clashtimer.core.SyncLog
import com.kuswa.clashtimer.core.SyncLogEntry
import com.kuswa.clashtimer.core.WidgetModel
import com.kuswa.clashtimer.core.WidgetModelBuilder
import com.kuswa.clashtimer.core.WidgetRow
import com.kuswa.clashtimer.sync.SyncRunner
import com.kuswa.clashtimer.widget.WidgetAlarm
import com.kuswa.clashtimer.widget.WidgetIcons
import com.kuswa.clashtimer.widget.WidgetRenderer
import com.kuswa.clashtimer.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SupportScreen(state: AppState, model: WidgetModel, log: SyncLog, timesCount: Int, now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var widgetCount by remember { mutableIntStateOf(-1) }
    var widgetSizes by remember { mutableStateOf("") }
    var refreshBusy by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    val exactAllowed = remember(now) { WidgetAlarm.canScheduleExact(context) }

    // The current state is a key: diagnostics never keep the initial, empty export captured.
    LaunchedEffect(state, now / 10) {
        widgetCount = runCatching { WidgetUpdater.widgetIds(context).size }.getOrDefault(-1)
        widgetSizes = runCatching { widgetSizeReport(context, state) }.getOrDefault("")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard("Last crash") { CrashSection() }
        }
        item {
            SectionCard("Widget diagnostics") {
                Text(when (widgetCount) {
                    -1 -> "Widgets placed: unknown"
                    0 -> "No Clash Timers widget is on the home screen yet."
                    else -> "Widgets placed: $widgetCount"
                })
                if (widgetSizes.isNotEmpty()) Text(widgetSizes, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${WidgetIcons.get(context).available.size} icons · upgrade times for $timesCount items",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(if (exactAllowed) "Exact alarms are allowed." else "Exact alarms are not allowed; updates use a 1-minute window.")
                if (!exactAllowed && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:${context.packageName}")),
                            )
                        }.onFailure { refreshError = "Could not open alarm settings: ${it.message}" }
                    }) { Text("Allow exact alarms") }
                }
                refreshError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = {
                        refreshBusy = true
                        refreshError = null
                        scope.launch {
                            try {
                                widgetCount = SyncRunner.refreshWidgets(context, "app")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                refreshError = "Widget refresh failed: ${e.message}"
                            } finally {
                                refreshBusy = false
                            }
                        }
                    },
                    enabled = !refreshBusy && widgetCount > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (refreshBusy) "Refreshing…" else "Refresh widget") }
                if (widgetCount == 0) Text("Add the widget from your launcher's widget picker to enable refresh.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Saved village") {
                state.export?.let {
                    Text("Village ${it.tag}")
                    Text("Export copied ${formatTimestamp(it.timestamp)}")
                } ?: Text("No village export saved yet. Use Sync on the Timers screen.")
            }
        }
        item {
            SectionCard("Widget text preview") {
                SelectionContainer {
                    Text(widgetPreview(model), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            SectionCard("Recent activity") {
                if (log.entries.isEmpty()) Text("No activity yet.")
                log.entries.forEach { entry ->
                    SelectionContainer {
                        Text(
                            SyncRunner.plainText(entry.line()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item { SectionCard("About Clash Timers") { AboutSection() } }
    }
}

private fun SyncLogEntry.line(): String {
    val mark = when (ok) { true -> "OK"; false -> "FAIL"; null -> "·" }
    return "${formatTimestamp(at)}  [$source] $mark  $message"
}

/** Each placed widget's size as the launcher reports it, and which row size it gets. */
private fun widgetSizeReport(context: android.content.Context, state: AppState): String {
    val manager = AppWidgetManager.getInstance(context)
    val rows = (state.export?.jobs?.size ?: 0) + 2
    return WidgetUpdater.widgetIds(context).joinToString("\n") { id ->
        val o = manager.getAppWidgetOptions(id)
        val w = WidgetRenderer.reportedWidthDp(o)
        val h = WidgetRenderer.reportedHeightDp(o)
        val size = if (w != null && h != null) "${w}×${h} dp" else "size not reported"
        val rowSize = if (WidgetRenderer.useCompactRows(o, rows)) "compact 25 dp rows" else "30 dp rows"
        "Widget #$id: $size · $rowSize (30 dp rows need about ${WidgetRenderer.normalHeightDp(rows)} dp)"
    }
}

/** The widget model as text, so a rendering problem can be told apart from a data problem. */
private fun widgetPreview(m: WidgetModel): String = SyncRunner.plainText(buildString {
    appendLine("${m.title}   [Sync]")
    if (!m.hasData) {
        append(if (m.syncFailed) WidgetModelBuilder.SYNC_FAILED else WidgetModelBuilder.NO_DATA)
        return@buildString
    }
    fun line(r: WidgetRow) {
        val time = if (r.kind == RowKind.RUNNING) Countdown.text(r.remaining) else r.staticStatus
        val button = when (r.button) {
            RowButton.NONE -> ""
            RowButton.LINK -> " [+]"
            RowButton.APPRENTICE -> " [Apprentice]"
            RowButton.LAB_ASSISTANT -> " [Assistant]"
        }
        val goblin = if (r.goblin) "GOBLIN " else ""
        val label = if (r.kind == RowKind.RUNNING) r.label else r.name // finished rows: name only
        appendLine("  $goblin$label | $time | ${(r.progress * 100).toInt()}%$button")
    }
    appendLine("${m.buildersLabel}${if (m.goblinWorking) " + Goblin" else ""}   [${m.bite.text}] [${m.builderPotion.text}]")
    m.builderRows.forEach(::line)
    appendLine("LAB   [${m.researchPotion.text}]")
    line(m.labRow)
    appendLine("PETS   [${m.petPotion.text}]")
    line(m.petRow)
    append(m.footerLeft.joinToString("") { it.text }).append("   ").append(m.footerRight)
})
