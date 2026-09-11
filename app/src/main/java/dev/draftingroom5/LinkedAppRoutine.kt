package dev.draftingroom5

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.net.URI

internal data class LinkedAppRoutineDraft(
    val name: String,
    val artworkId: String,
    val packageName: String,
)

internal fun Routine.linkedAppDraft(): LinkedAppRoutineDraft {
    require(execution == RoutineExecution.LINKED_APP)
    return LinkedAppRoutineDraft(name, artworkId, checkNotNull(appLink).packageName)
}

internal fun LinkedAppRoutineDraft.isValid(): Boolean =
    name.trim().isNotEmpty() && name.trim().length <= 200 && validPackageName(packageName)

internal fun Routine.withLinkedAppDraft(draft: LinkedAppRoutineDraft): Routine? {
    if (execution != RoutineExecution.LINKED_APP || !draft.isValid()) return null
    return copy(
        revision = revision + 1,
        name = draft.name.trim(),
        artworkId = RoutineArtworkCatalog.resolve(draft.artworkId).storageId,
        appLink = AppLink(draft.packageName.trim(), appLink?.deepLink.takeIf { draft.packageName == appLink?.packageName }),
    )
}

internal sealed interface LinkedAppLaunchTarget {
    data class DeepLink(val uri: String, val packageName: String) : LinkedAppLaunchTarget
    data class Launcher(val packageName: String) : LinkedAppLaunchTarget
}

internal enum class LinkedAppLaunchFailure { INVALID_PACKAGE, UNAVAILABLE }

internal sealed interface LinkedAppLaunchResult {
    data class Opened(val target: LinkedAppLaunchTarget) : LinkedAppLaunchResult
    data class Failed(val reason: LinkedAppLaunchFailure) : LinkedAppLaunchResult
}

internal fun validPackageName(value: String): Boolean =
    value.length in 3..255 && PACKAGE_NAME.matches(value)

internal fun validDeepLink(value: String): Boolean = runCatching {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
    value.length <= 2048 && uri.isAbsolute && scheme !in setOf("intent", "file", "content", "javascript", "data", "http") &&
        uri.rawUserInfo == null && !value.any { it.isWhitespace() || it.isISOControl() } &&
        (scheme != "https" || !uri.host.isNullOrBlank())
}.getOrDefault(false)

internal fun linkedAppLaunchTargets(link: AppLink): List<LinkedAppLaunchTarget> {
    if (!validPackageName(link.packageName)) return emptyList()
    return buildList {
        link.deepLink?.takeIf(::validDeepLink)?.let { add(LinkedAppLaunchTarget.DeepLink(it, link.packageName)) }
        add(LinkedAppLaunchTarget.Launcher(link.packageName))
    }
}

internal fun launchLinkedApp(
    link: AppLink,
    attempt: (LinkedAppLaunchTarget) -> Boolean,
): LinkedAppLaunchResult {
    val targets = linkedAppLaunchTargets(link)
    if (targets.isEmpty()) return LinkedAppLaunchResult.Failed(LinkedAppLaunchFailure.INVALID_PACKAGE)
    targets.forEach { target -> if (runCatching { attempt(target) }.getOrDefault(false)) return LinkedAppLaunchResult.Opened(target) }
    return LinkedAppLaunchResult.Failed(LinkedAppLaunchFailure.UNAVAILABLE)
}

internal fun launchLinkedApp(context: Context, routine: Routine): LinkedAppLaunchResult =
    launchLinkedApp(checkNotNull(routine.appLink)) { target ->
        val intent = when (target) {
            is LinkedAppLaunchTarget.DeepLink -> Intent(Intent.ACTION_VIEW, Uri.parse(target.uri)).setPackage(target.packageName)
            is LinkedAppLaunchTarget.Launcher -> context.packageManager.getLaunchIntentForPackage(target.packageName)
        } ?: return@launchLinkedApp false
        context.startActivity(intent)
        true
    }

internal fun linkedAppRecoveryMessage(routine: Routine, result: LinkedAppLaunchResult.Failed): String = when (result.reason) {
    LinkedAppLaunchFailure.INVALID_PACKAGE -> "${routine.name} has an invalid app target. Choose another app to continue."
    LinkedAppLaunchFailure.UNAVAILABLE -> "${routine.name} could not open its linked app. It may be uninstalled or unavailable."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkedAppRoutineEditorScreen(
    routine: Routine,
    replacement: InstalledAppOption?,
    scheduleSummary: String,
    onChooseApp: () -> Unit,
    onSave: (Routine) -> Boolean,
    onDelete: () -> Unit,
    onTestLink: (Routine) -> LinkedAppLaunchResult,
    onBack: () -> Unit,
) {
    val original = remember(routine.id, routine.revision) { routine.linkedAppDraft() }
    var name by rememberSaveable(routine.id, routine.revision) { mutableStateOf(original.name) }
    var artworkId by rememberSaveable(routine.id, routine.revision) { mutableStateOf(original.artworkId) }
    var packageName by rememberSaveable(routine.id, routine.revision) { mutableStateOf(original.packageName) }
    var artworkPicker by rememberSaveable(routine.id) { mutableStateOf(false) }
    var deleteRequested by rememberSaveable(routine.id) { mutableStateOf(false) }
    var discardRequested by rememberSaveable(routine.id) { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var actionMessage by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val selectedLabel = replacement?.takeIf { it.packageName == packageName }?.label
        ?: remember(packageName) { installedAppLabel(context, packageName) }
        ?: linkedAppDisplayName(packageName)
    val launchable = remember(packageName) { isPackageLaunchable(context, packageName) }
    val draft = LinkedAppRoutineDraft(name, artworkId, packageName)
    val changed = draft != original
    val requestBack = {
        if (!changed) onBack() else discardRequested = true
    }
    BackHandler(onBack = requestBack)
    LaunchedEffect(replacement?.packageName) {
        replacement?.let {
            packageName = it.packageName
            actionMessage = "${it.label} selected. Save changes to finish."
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            SecondaryTopBar("Routine editor", requestBack) {
                Box {
                    IconButton(onClick = { overflowExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete routine") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { overflowExpanded = false; deleteRequested = true },
                        )
                    }
                }
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LINKED APP", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (name.isBlank()) "Untitled routine" else name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(scheduleSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f &&
                    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 360) Image(
                    painter = painterResource(RoutineArtworkCatalog.resolve(artworkId).resource(RoutineArtworkCrop.HEADER)),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 200) name = it },
                label = { Text("Routine name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                supportingText = { if (name.isBlank()) Text("Enter a routine name") },
            )
            OutlinedButton(onClick = { artworkPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Edit, null)
                Spacer(Modifier.size(8.dp))
                Text("Change artwork")
            }
            AppSurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("APP CONNECTION", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InstalledAppIcon(packageName)
                        Column(Modifier.weight(1f)) {
                            Text(selectedLabel, fontWeight = FontWeight.Bold)
                            Text(if (launchable) "Installed · Ready to open" else "App not installed", color = if (launchable) AppMint else AppGold)
                        }
                    }
                    OutlinedButton(onClick = onChooseApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Change app") }
                    OutlinedButton(
                        onClick = {
                            val candidate = routine.withLinkedAppDraft(draft)
                            actionMessage = when (val result = candidate?.let(onTestLink)) {
                                is LinkedAppLaunchResult.Opened -> "App link opened successfully."
                                is LinkedAppLaunchResult.Failed -> linkedAppRecoveryMessage(routine, result)
                                null -> "Enter a valid name and choose an app first."
                            }
                        },
                        enabled = draft.isValid(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Test app link") }
                }
            }
            actionMessage?.let { Text(it, color = AppMint, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                Button(
                    enabled = draft.isValid() && changed,
                    onClick = { routine.withLinkedAppDraft(draft)?.let { if (onSave(it)) actionMessage = "Changes saved." } },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Save changes") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (artworkPicker) RoutineArtworkPickerSheet(
        selectedId = artworkId,
        onDismiss = { artworkPicker = false },
        onDone = { artworkId = it; artworkPicker = false },
    )
    if (deleteRequested) AppConfirmationDialog(
        title = "Delete ${routine.name}?",
        message = "This deletes the routine, every scheduled entry that references it, and its saved incomplete sessions. Completed workout history remains available.",
        confirmLabel = "Delete routine",
        onConfirm = onDelete,
        onDismiss = { deleteRequested = false },
    )
    if (discardRequested) AppConfirmationDialog(
        title = "Discard unsaved changes?",
        message = "Your saved routine will stay unchanged.",
        confirmLabel = "Discard changes",
        onConfirm = onBack,
        onDismiss = { discardRequested = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineArtworkPickerSheet(
    selectedId: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var pending by rememberSaveable(selectedId) { mutableStateOf(selectedId) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose artwork", style = MaterialTheme.typography.headlineSmall)
            Text("Designed to stay consistent across session cards.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            RoutineArtworkCatalog.entries.chunked(2).forEach { rowAssets ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowAssets.forEach { asset ->
                        val isSelected = asset.storageId == pending
                        val assetName = stringResource(asset.displayNameRes)
                        Card(
                            onClick = { pending = asset.storageId },
                            modifier = Modifier.weight(1f).heightIn(min = 132.dp).semantics {
                                selected = isSelected
                                contentDescription = "$assetName, ${if (isSelected) "Selected" else "Not selected"}"
                            },
                            colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(asset.resource(RoutineArtworkCrop.PICKER)), null, Modifier.size(82.dp), contentScale = ContentScale.Fit)
                                Text(stringResource(asset.displayNameRes), style = MaterialTheme.typography.labelMedium)
                                if (isSelected) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, null, tint = AppMint, modifier = Modifier.size(16.dp))
                                    Text("Selected", color = AppMint, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (rowAssets.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                Button(onClick = { onDone(pending) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun InstalledAppIcon(packageName: String) {
    val manager = LocalContext.current.packageManager
    val image = remember(packageName) { runCatching { manager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    if (image == null) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Apps, contentDescription = null, tint = AppBlue, modifier = Modifier.size(30.dp))
    } else Image(image, contentDescription = null, modifier = Modifier.size(48.dp))
}

private fun installedAppLabel(context: Context, packageName: String): String? = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString().trim().ifBlank { null }
}.getOrNull()

private fun isPackageLaunchable(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)

private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
