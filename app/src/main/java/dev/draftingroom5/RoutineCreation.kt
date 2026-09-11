package dev.draftingroom5

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AddRoutineChoice { GUIDED, LINKED_APP }

internal data class AddRoutineOption(
    val choice: AddRoutineChoice,
    val title: String,
    val description: String,
)

internal val addRoutineOptions = listOf(
    AddRoutineOption(
        AddRoutineChoice.GUIDED,
        "Guided routine",
        "Build a list of exercises or activities to complete here.",
    ),
    AddRoutineOption(
        AddRoutineChoice.LINKED_APP,
        "Linked-app routine",
        "Open another app when it’s time to begin.",
    ),
)

internal data class InstalledAppOption(
    val packageName: String,
    val label: String,
    val category: String,
)

internal enum class PackageQueryApi { LEGACY_FLAGS, TYPED_FLAGS }

internal fun packageQueryApi(sdkInt: Int): PackageQueryApi =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) PackageQueryApi.TYPED_FLAGS else PackageQueryApi.LEGACY_FLAGS

internal fun normalizeInstalledApps(
    candidates: List<InstalledAppOption>,
    ownPackageName: String,
): List<InstalledAppOption> = candidates
    .filter { it.packageName.isNotBlank() && it.packageName != ownPackageName && it.label.isNotBlank() }
    .distinctBy { it.packageName }
    .sortedWith(compareBy(java.text.Collator.getInstance()) { it.label })

internal fun filterInstalledApps(apps: List<InstalledAppOption>, query: String): List<InstalledAppOption> {
    val needle = query.trim()
    return if (needle.isEmpty()) apps else apps.filter { it.label.contains(needle, ignoreCase = true) }
}

internal data class RoutineDraft(
    val id: String,
    val execution: RoutineExecution,
    val packageName: String? = null,
    val appLabel: String? = null,
)

internal fun RoutineDraft.withSelectedApp(app: InstalledAppOption): RoutineDraft {
    require(execution == RoutineExecution.LINKED_APP) { "Only linked-app drafts can select an app." }
    return copy(packageName = app.packageName, appLabel = app.label)
}

/** The only boundary that converts a transient linked-app draft into canonical plan data. */
internal fun RoutineDraft.savedLinkedRoutine(name: String, artworkId: String = RoutineArtworkCatalog.FALLBACK_ID): Routine? {
    val cleanName = name.trim()
    val cleanPackage = packageName?.trim().orEmpty()
    if (execution != RoutineExecution.LINKED_APP || cleanName.isEmpty() || cleanPackage.isEmpty()) return null
    if (cleanName.length > 200 || !validPackageName(cleanPackage)) return null
    return Routine(
        id = id,
        revision = 1,
        name = cleanName,
        artworkId = RoutineArtworkCatalog.resolve(artworkId).storageId,
        execution = RoutineExecution.LINKED_APP,
        exercises = emptyList(),
        appLink = AppLink(cleanPackage, null),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddRoutineChooserSheet(
    onChoose: (AddRoutineChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add routine", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text("Choose how this routine starts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            addRoutineOptions.forEach { option ->
                Card(
                    onClick = { onChoose(option.choice) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp).semantics {
                        contentDescription = "${option.title}. ${option.description}"
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            if (option.choice == AddRoutineChoice.GUIDED) Icons.Default.FitnessCenter else Icons.Default.Link,
                            contentDescription = null,
                            tint = AppBlue,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(option.title, fontWeight = FontWeight.Bold)
                            Text(option.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}

private sealed interface InstalledAppLoadState {
    data object Loading : InstalledAppLoadState
    data class Ready(val apps: List<InstalledAppOption>) : InstalledAppLoadState
    data class Failed(val message: String) : InstalledAppLoadState
}

@Composable
internal fun InstalledAppPickerScreen(
    onSelect: (InstalledAppOption) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<InstalledAppLoadState>(InstalledAppLoadState.Loading) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(refreshKey, context.packageName) {
        state = InstalledAppLoadState.Loading
        state = try {
            val apps = withContext(Dispatchers.IO) {
                queryLaunchableApps(context.packageManager, context.packageName)
            }
            InstalledAppLoadState.Ready(apps)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            InstalledAppLoadState.Failed(error.message ?: "Android could not list launchable apps.")
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Choose app", onBack) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.size(2.dp))
                EditorialHeading(
                    eyebrow = "Linked app",
                    title = "Choose an installed app",
                    supportingText = "This selects an app to open. It does not connect an account or grant DraftingRoom5 access.",
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
                        }
                    } else null,
                )
            }
            when (val current = state) {
                InstalledAppLoadState.Loading -> item { InlineLoadingState("Finding apps you can open…") }
                is InstalledAppLoadState.Failed -> item {
                    InlineErrorState("Couldn’t load apps", current.message, "Try again", onAction = { refreshKey += 1 })
                }
                is InstalledAppLoadState.Ready -> {
                    val visible = filterInstalledApps(current.apps, query)
                    if (visible.isEmpty()) item {
                        AppSurfaceCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (query.isBlank()) "No launchable apps found" else "No apps match your search", fontWeight = FontWeight.Bold)
                                Text(
                                    if (query.isBlank()) "Install an app with a standard launcher, then try again." else "Try another app name.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else items(visible, key = { it.packageName }) { app ->
                        InstalledAppRow(app, onSelect)
                    }
                }
            }
            item { Spacer(Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun InstalledAppRow(app: InstalledAppOption, onSelect: (InstalledAppOption) -> Unit) {
    val packageManager = LocalContext.current.packageManager
    val image = remember(app.packageName) {
        runCatching { packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    AppSurfaceCard(
        Modifier.fillMaxWidth().clickable { onSelect(app) }.heightIn(min = 76.dp).semantics {
            contentDescription = "${app.label}, ${app.category}, Installed"
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (image == null) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = AppBlue, modifier = Modifier.size(30.dp))
                }
            } else {
                Image(image, contentDescription = null, modifier = Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, fontWeight = FontWeight.Bold)
                Text(app.category, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Installed", color = AppMint, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
internal fun NewRoutineDraftScreen(
    draft: RoutineDraft,
    onChooseApp: () -> Unit,
    onTestLink: (Routine) -> LinkedAppLaunchResult,
    onSaveLinked: (String, String) -> Boolean,
    onDiscard: () -> Unit,
) {
    var name by rememberSaveable(draft.id) { mutableStateOf(draft.appLabel?.let { "$it routine" }.orEmpty()) }
    var artworkId by rememberSaveable(draft.id) { mutableStateOf(RoutineArtworkCatalog.FALLBACK_ID) }
    var artworkPicker by rememberSaveable(draft.id) { mutableStateOf(false) }
    var actionMessage by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var discardRequested by rememberSaveable(draft.id) { mutableStateOf(false) }
    val requestBack = { if (name.isNotBlank() || draft.packageName != null) discardRequested = true else onDiscard() }
    BackHandler(onBack = requestBack)
    val candidate = draft.savedLinkedRoutine(name, artworkId)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Routine editor", requestBack) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorialHeading(
                eyebrow = if (draft.execution == RoutineExecution.GUIDED) "Guided routine" else "Linked app",
                title = if (name.isBlank()) "New routine" else name,
                supportingText = if (draft.execution == RoutineExecution.GUIDED) {
                    "Add at least one exercise before this routine can be saved."
                } else {
                    "${draft.appLabel ?: "Selected app"} is selected but nothing has been saved yet."
                },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 200) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Routine name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
            )
            Image(
                painter = painterResource(RoutineArtworkCatalog.resolve(artworkId).resource(RoutineArtworkCrop.HEADER)),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
            OutlinedButton(onClick = { artworkPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Change artwork")
            }
            if (draft.execution == RoutineExecution.LINKED_APP) {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("APP CONNECTION", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(draft.appLabel ?: "No app selected", fontWeight = FontWeight.Bold)
                        Text("Installed · Ready to open", color = AppMint)
                    }
                }
                OutlinedButton(onClick = onChooseApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Change app")
                }
                OutlinedButton(
                    enabled = candidate != null,
                    onClick = {
                        actionMessage = when (val result = candidate?.let(onTestLink)) {
                            is LinkedAppLaunchResult.Opened -> "App link opened successfully."
                            is LinkedAppLaunchResult.Failed -> linkedAppRecoveryMessage(candidate, result)
                            null -> "Enter a valid name and choose an app first."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Test app link") }
                Button(
                    enabled = candidate != null,
                    onClick = { onSaveLinked(name, artworkId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Save routine") }
            } else {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("EXERCISES", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text("No exercises yet", fontWeight = FontWeight.Bold)
                        Text("The exercise builder adds the first valid exercise before saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            actionMessage?.let { Text(it, color = AppMint, style = MaterialTheme.typography.bodySmall) }
            Text("Draft only · not saved", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(24.dp))
        }
    }
    if (discardRequested) AppConfirmationDialog(
        title = "Discard this routine draft?",
        message = "The routine has not been saved. Your draft name and app selection will be discarded.",
        confirmLabel = "Discard draft",
        onConfirm = onDiscard,
        onDismiss = { discardRequested = false },
    )
    if (artworkPicker) RoutineArtworkPickerSheet(
        selectedId = artworkId,
        onDismiss = { artworkPicker = false },
        onDone = { artworkId = it; artworkPicker = false },
    )
}

@Suppress("DEPRECATION")
private fun queryLaunchableApps(packageManager: PackageManager, ownPackageName: String): List<InstalledAppOption> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0),
        )
    } else {
        packageManager.queryIntentActivities(intent, 0)
    }
    return normalizeInstalledApps(
        candidates = resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!safeLauncherActivity(activity.enabled, activity.applicationInfo.enabled, activity.exported,
                    activity.permission.isNullOrEmpty() || packageManager.checkPermission(activity.permission, ownPackageName) == PackageManager.PERMISSION_GRANTED)) return@mapNotNull null
            val packageName = activity.packageName ?: return@mapNotNull null
            val label = info.loadLabel(packageManager).toString().trim().ifBlank { packageName }
            InstalledAppOption(packageName, label, appCategory(activity.applicationInfo.category))
        },
        ownPackageName = ownPackageName,
    )
}

internal fun safeLauncherActivity(enabled: Boolean, appEnabled: Boolean, exported: Boolean, permissionGranted: Boolean): Boolean =
    enabled && appEnabled && exported && permissionGranted

private fun appCategory(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "Game"
    ApplicationInfo.CATEGORY_AUDIO -> "Audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photo"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps & navigation"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    else -> "App"
}
