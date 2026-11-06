package com.kuswa.clashtimer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kuswa.clashtimer.core.AppSettings
import com.kuswa.clashtimer.core.BoostSettings
import com.kuswa.clashtimer.core.GameIds
import com.kuswa.clashtimer.core.VillageExport
import com.kuswa.clashtimer.notify.Notifier

private const val H = 3600L

/** Settings retain their existing storage and ranges; only the editing surface changes. */
@Composable
fun SettingsSection(
    settings: AppSettings,
    export: VillageExport?,
    now: Long,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val b = settings.boosts
    fun boosts(f: (BoostSettings) -> BoostSettings) = onChange { it.copy(boosts = f(it.boosts)) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val notificationsAllowed = remember(now, permissionTick) { Notifier.allowed(context) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Changes save automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard("Your village") {
            SettingRow("Player name", settings.displayName, "Shown in the app and on your widget") { editName = true }
        }
        SectionCard("Notifications") {
            SwitchRow("Upgrade finish alerts", settings.notificationsEnabled) { on ->
                onChange { it.copy(notificationsEnabled = on) }
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.allowed(context)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Text("Get an alert when a builder, the laboratory or the pet house is free.", style = MaterialTheme.typography.bodyMedium)
            if (settings.notificationsEnabled && !notificationsAllowed) {
                Text("Notifications are blocked by Android.", color = MaterialTheme.colorScheme.error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                }
                OutlinedButton(onClick = { openNotificationSettings(context); permissionTick++ }) { Text("Notification settings") }
            }
            Text(
                "For reliable alerts after a reboot, allow Auto-start and unrestricted battery use in Android app settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { openAppSettings(context) }) { Text("Battery and auto-start settings") }
        }
        SectionCard("Builders") {
            val derived = export?.derivedTotalBuilders ?: AppSettings.DEFAULT_TOTAL_BUILDERS
            NumberSetting(
                title = "Total builders",
                value = settings.totalBuildersOverride,
                automatic = derived,
                range = 1..10,
                unit = "Builders",
                description = "Automatic uses Builder's Huts and B.O.B's Hut from the export.",
                automaticLabel = "Use automatic builder count",
            ) { value -> onChange { it.copy(totalBuildersOverride = value) } }
            SwitchRow("Boost the Goblin Builder too", b.boostsApplyToGoblin) { on ->
                boosts { it.copy(boostsApplyToGoblin = on) }
            }
        }
        SectionCard("Potions and snacks") {
            Text("Hours saved for each hour of use. For example, 9 hours saved means 10× speed.", style = MaterialTheme.typography.bodyMedium)
            HoursSetting("Builder potion", b.builderPotionSavingSec, 1..47) { value -> boosts { it.copy(builderPotionSavingSec = value) } }
            HoursSetting("Builder Bite", b.builderBiteSavingSec, 1..23) { value -> boosts { it.copy(builderBiteSavingSec = value) } }
            HoursSetting("Research potion", b.researchPotionSavingSec, 1..47) { value -> boosts { it.copy(researchPotionSavingSec = value) } }
            HoursSetting("Pet potion", b.petPotionSavingSec, 1..47) { value -> boosts { it.copy(petPotionSavingSec = value) } }
        }
        SectionCard("Helpers") {
            Text("Automatic savings use the helper's level in your village export.", style = MaterialTheme.typography.bodyMedium)
            NumberSetting(
                title = "Builder's Apprentice",
                value = b.apprenticeSavingSecOverride?.div(H)?.toInt(),
                automatic = export?.helper(GameIds.HELPER_BUILDERS_APPRENTICE)?.level,
                range = 1..24,
                unit = "Hours saved per session",
                automaticLabel = "Use helper level from export",
            ) { value -> boosts { it.copy(apprenticeSavingSecOverride = value?.times(H)) } }
            NumberSetting(
                title = "Lab Assistant",
                value = b.labAssistantSavingSecOverride?.div(H)?.toInt(),
                automatic = export?.helper(GameIds.HELPER_LAB_ASSISTANT)?.level,
                range = 1..24,
                unit = "Hours saved per session",
                automaticLabel = "Use helper level from export",
            ) { value -> boosts { it.copy(labAssistantSavingSecOverride = value?.times(H)) } }
            NumberSetting(
                title = "Helper session interval",
                value = (b.helperPeriodSec / H).toInt(),
                range = 2..48,
                unit = "Hours",
                description = "Time between helper sessions; the default is 23 hours.",
            ) { value -> value?.let { hours -> boosts { it.copy(helperPeriodSec = hours * H) } } }
        }
        SectionCard("Widget compatibility") {
            SwitchRow("Live seconds countdown", settings.liveCountdown) { on -> onChange { it.copy(liveCountdown = on) } }
            Text(
                "Only affects the home-screen widget. Turn this off if your launcher does not display countdowns correctly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Reset settings")
        }
    }
    if (editName) {
        PlayerNameDialog(
            current = settings.playerName,
            firstLaunch = false,
            onDone = { name -> onChange { it.copy(playerName = name) }; editName = false },
            onDismiss = { editName = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("Restore builder, boost, helper and notification defaults. Your name, saved village and widget countdown preference will stay unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    onChange { AppSettings(liveCountdown = it.liveCountdown, playerName = it.playerName) }
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HoursSetting(title: String, seconds: Long, range: IntRange, onSet: (Long) -> Unit) {
    NumberSetting(
        title = title,
        value = (seconds / H).toInt(),
        range = range,
        unit = "Hours saved",
        description = "${seconds / H + 1}× speed",
    ) { value -> value?.let { onSet(it * H) } }
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int?,
    range: IntRange,
    unit: String,
    description: String? = null,
    automatic: Int? = null,
    automaticLabel: String? = null,
    onSet: (Int?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val valueLabel = when {
        value != null -> "$value ${unit.lowercase()}"
        automatic != null -> "Automatic · $automatic ${unit.lowercase()}"
        else -> "Automatic · helper not found in export"
    }
    SettingRow(title, valueLabel, description) { editing = true }
    if (editing) {
        SettingValueDialog(
            title = title,
            initialValue = value ?: automatic ?: range.first,
            range = range,
            unit = unit,
            automaticLabel = automaticLabel,
            onSave = { onSet(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, description: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Edit $title", onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
