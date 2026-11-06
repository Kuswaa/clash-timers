@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kuswa.clashtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuswa.clashtimer.R
import com.kuswa.clashtimer.core.Countdown
import com.kuswa.clashtimer.core.RowButton
import com.kuswa.clashtimer.core.RowKind
import com.kuswa.clashtimer.core.SyncLogEntry
import com.kuswa.clashtimer.core.WidgetModel
import com.kuswa.clashtimer.sync.SyncRunner

@Composable
fun TimerDashboard(
    model: WidgetModel,
    lastSync: SyncLogEntry?,
    busy: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(TimerFilter.ALL) }
    val timers = remember(model, filter) { model.timers(filter) }

    Column(modifier.fillMaxSize()) {
        if (model.hasData) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimerFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(entry.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (model.hasData) {
                item(key = "village") { VillageSummary(model) }
            } else {
                item(key = "welcome") {
                    SectionCard("Your village, at a glance") {
                        Text("Bring your upgrades into one clear view.", style = MaterialTheme.typography.bodyLarge)
                        Text("1. Open Clash of Clans.")
                        Text("2. Go to Settings > Data Export > Copy.")
                        Text("3. Return here and sync from your clipboard.")
                        Text(
                            "No account or internet connection needed. Your village stays on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "sync") { SyncCard(lastSync, busy, onSync) }
            if (model.hasData) {
                item(key = "heading") {
                    Text(
                        if (filter == TimerFilter.ALL) "Village upgrades" else "${filter.label} upgrades",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (timers.isEmpty()) {
                    item(key = "idle") {
                        SectionCard("All builders are free") {
                            Text("No builder upgrades in the saved export. Sync again after starting an upgrade in the game.")
                        }
                    }
                }
                items(timers, key = { "${it.category}:${it.row.jobKey ?: it.row.name}" }) { timer ->
                    TimerCard(timer)
                }
                item(key = "prediction-note") {
                    Text(
                        "Finish times include planned helpers and boosts. Sync after changes in the game to keep them accurate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VillageSummary(model: WidgetModel) {
    val next = model.nextRunningTimer()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                model.playerName + (model.townHall?.let { " · Town Hall $it" } ?: ""),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                "${(model.buildersTotal - model.buildersBusy).coerceAtLeast(0)} builders free · ${model.buildersBusy}/${model.buildersTotal} busy" +
                    if (model.goblinWorking) " · Goblin active" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (next != null) {
                Text("Next finish: ${next.row.name}", style = MaterialTheme.typography.titleSmall)
                next.row.finishAt?.let { Text(formatTimestamp(it), style = MaterialTheme.typography.bodyMedium) }
            } else {
                Text("No upgrades running in your saved village.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SyncCard(last: SyncLogEntry?, busy: Boolean, onSync: () -> Unit) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val failed = last?.ok == false
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when { busy -> "Syncing your village…"; failed -> "Sync failed"; last != null -> "Village synced"; else -> "Ready when you are" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    last?.let { Text(formatTimestamp(it.at), style = MaterialTheme.typography.bodySmall) }
                }
                if (last != null && !failed) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide details" else "Sync details")
                    }
                }
            }
            if (failed) {
                last?.let { SelectionContainer { Text(SyncRunner.plainText(it.message), style = MaterialTheme.typography.bodyMedium) } }
                Text("Copy fresh village data in Clash, then try again.", style = MaterialTheme.typography.bodyMedium)
            }
            if (last == null) {
                Text("Your clipboard is read only when you tap Sync.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSync,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    if (busy) "Syncing…" else if (failed) "Retry from clipboard" else "Sync from clipboard",
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
            if (last != null && !failed && showDetails) {
                SelectionContainer { Text(SyncRunner.plainText(last.message), style = MaterialTheme.typography.bodyMedium) }
                Text("Copy fresh data in Clash before syncing. The clipboard is read only when you tap Sync.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimerCard(timer: DashboardTimer) {
    val row = timer.row
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                timer.category.label + if (row.goblin) " · Goblin Builder" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(SyncRunner.plainText(row.label), style = MaterialTheme.typography.titleLargeEmphasized)
            BasicText(
                if (row.kind == RowKind.RUNNING) Countdown.text(row.remaining) else row.staticStatus,
                style = MaterialTheme.typography.displaySmallEmphasized.copy(
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.primary,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 36.sp),
            )
            if (row.kind != RowKind.IDLE) {
                LinearWavyProgressIndicator(
                    progress = { row.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${(row.progress.coerceIn(0f, 1f) * 100).toInt()}% complete", style = MaterialTheme.typography.bodySmall)
            }
            if (row.kind == RowKind.RUNNING) {
                row.finishAt?.let {
                    Text("Finishes ${formatTimestamp(it)}", style = MaterialTheme.typography.bodyMedium)
                }
                val helper = when (row.button) {
                    RowButton.APPRENTICE -> "Builder's Apprentice assigned"
                    RowButton.LAB_ASSISTANT -> "Lab Assistant assigned"
                    else -> null
                }
                helper?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            } else {
                Text(
                    if (row.kind == RowKind.COMPLETED) "Ready for the next upgrade. Sync after starting it in the game."
                    else "No upgrade in the saved export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
