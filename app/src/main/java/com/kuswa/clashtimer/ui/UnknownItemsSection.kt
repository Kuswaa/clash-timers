package com.kuswa.clashtimer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuswa.clashtimer.core.Fallbacks
import com.kuswa.clashtimer.core.UnknownItem
import com.kuswa.clashtimer.core.UnknownItems
import com.kuswa.clashtimer.core.UserItems
import com.kuswa.clashtimer.core.VillageExport
import com.kuswa.clashtimer.data.Names
import com.kuswa.clashtimer.data.StateRepository
import com.kuswa.clashtimer.sync.SyncRunner
import com.kuswa.clashtimer.widget.WidgetIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Unknown items" (plan 1.1 §4): every item in the latest export(s) that the app has no name or no icon
 * for. The player can type a name and pick an image (Android photo picker, no storage permission); both
 * are stored on the phone only and beat the bundled data, so a game update doesn't need a new release.
 */
@Composable
fun UnknownItemsSection(exports: List<VillageExport>, user: UserItems) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StateRepository(context) }
    val icons = remember { WidgetIcons.get(context) }
    val items = remember(exports, user) {
        UnknownItems.find(exports, Names.get(context), { icons.hasBundled(it.toString()) }, user)
    }
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pickingFor ?: return@rememberLauncherForActivityResult
        pickingFor = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { decodeSmall(context, uri)?.let { icons.saveUserIcon(id, it) } ?: false }.getOrDefault(false)
            }
            if (ok) repo.updateUserItems { it.copy(icons = it.icons + id) }
            message = if (ok) "Image saved for $id." else "Couldn't read that image."
            repo.log("app", if (ok) null else false, if (ok) "Own icon set for $id" else "Own icon for $id failed")
            SyncRunner.refreshWidgets(context, "app")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Unknown items", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (exports.isEmpty()) {
            Text("Sync once to see your village's items here.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (items.isEmpty()) {
            Text("Every item in your village has a name and an icon.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Text(
            "Items from a game update that this version doesn't know yet. Give them a name or an image; " +
                "they stay on this phone and show on the widget right away.",
            style = MaterialTheme.typography.bodySmall,
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        for (item in items) {
            UnknownItemRow(
                item = item,
                preview = remember(item, user) { icons.userIcon(item.item.dataId.toString())?.let { BitmapFactory.decodeFile(it.path) } },
                onSaveName = { name ->
                    scope.launch {
                        repo.updateUserItems { it.withName(item.item.dataId, name) }
                        repo.log("app", null, "Own name for ${item.item.dataId}: ${name.trim().ifEmpty { "(removed)" }}")
                        SyncRunner.refreshWidgets(context, "app")
                    }
                },
                onPick = {
                    pickingFor = item.item.dataId
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveIcon = {
                    scope.launch {
                        withContext(Dispatchers.IO) { icons.deleteUserIcon(item.item.dataId) }
                        repo.updateUserItems { it.copy(icons = it.icons - item.item.dataId) }
                        SyncRunner.refreshWidgets(context, "app")
                    }
                },
            )
        }
    }
}

@Composable
private fun UnknownItemRow(
    item: UnknownItem,
    preview: Bitmap?,
    onSaveName: (String) -> Unit,
    onPick: () -> Unit,
    onRemoveIcon: () -> Unit,
) {
    var text by remember(item.item.dataId, item.userName) { mutableStateOf(item.userName ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val missing = listOfNotNull("no name".takeIf { item.missingName }, "no icon".takeIf { item.missingIcon })
        Text(
            "${Fallbacks.kindLabel(item.item.kind)} ${item.item.dataId} · ${missing.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(UserItems.MAX_NAME) },
                label = { Text(if (item.missingName) "Name" else "Own name (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSaveName(text) }, enabled = text.trim() != (item.userName ?: "")) { Text("Save") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (preview != null) {
                Image(preview.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
            }
            OutlinedButton(onClick = onPick) { Text(if (item.hasUserIcon) "Change image" else "Pick image") }
            if (item.hasUserIcon) TextButton(onClick = onRemoveIcon) { Text("Remove image") }
        }
    }
}

/** Decodes [uri] at a size just big enough for a 64×64 icon, so a large photo can't run out of memory. */
private fun decodeSmall(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val side = minOf(bounds.outWidth, bounds.outHeight)
    if (side <= 0) return null
    var sample = 1
    while (side / (sample * 2) >= WidgetIcons.USER_ICON_PX * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}
