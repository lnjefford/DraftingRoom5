package dev.draftingroom5

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Shared workspace navigation shell. Every workspace opens the same left drawer from the app mark. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WorkspaceDrawer(
    active: AppWorkspace,
    onSelect: (AppWorkspace) -> Unit,
    updatePresentation: UpdateSettingsPresentation = updateSettingsPresentation(AppUpdateStatus(), false, null, BuildConfig.VERSION_NAME),
    onUpdate: () -> Unit = {},
    backupStatus: AutomaticBackupStatus = AutomaticBackupStatus(),
    backupActionMessage: String? = null,
    onAutomaticBackupChange: (Boolean) -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onRestoreLatest: () -> Unit = {},
    onOpenBackupSettings: () -> Unit = {},
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun closeAndSelect(workspace: AppWorkspace) {
        scope.launch {
            drawerState.close()
            if (workspace != active) onSelect(workspace)
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(304.dp).fillMaxHeight()) {
                WorkspaceDrawerContent(
                    active = active,
                    updatePresentation = updatePresentation,
                    onSelect = ::closeAndSelect,
                    onUpdate = onUpdate,
                    backupStatus = backupStatus,
                    backupActionMessage = backupActionMessage,
                    onAutomaticBackupChange = onAutomaticBackupChange,
                    onBackUpNow = onBackUpNow,
                    onRestoreLatest = onRestoreLatest,
                    onOpenBackupSettings = onOpenBackupSettings,
                )
            }
        },
    ) {
        content { scope.launch { drawerState.open() } }
    }
}

@Composable
internal fun WorkspaceDrawerContent(
    active: AppWorkspace,
    updatePresentation: UpdateSettingsPresentation,
    onSelect: (AppWorkspace) -> Unit,
    onUpdate: () -> Unit,
    backupStatus: AutomaticBackupStatus = AutomaticBackupStatus(),
    backupActionMessage: String? = null,
    onAutomaticBackupChange: (Boolean) -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onRestoreLatest: () -> Unit = {},
    onOpenBackupSettings: () -> Unit = {},
    initialBackupExpanded: Boolean = false,
) {
    var backupExpanded by remember { mutableStateOf(initialBackupExpanded) }
    var confirmRestore by remember { mutableStateOf(false) }
    if (confirmRestore) AppConfirmationDialog(
        title = "Restore recovery snapshot?",
        message = "This replaces Fitness and Finance data with the latest recovery copy. Health Connect data, permissions, and linked-account credentials are unaffected.",
        confirmLabel = "Restore",
        onConfirm = { confirmRestore = false; onRestoreLatest() },
        onDismiss = { confirmRestore = false },
    )
    val backupPresentation = backupSettingsPresentation(
        backupStatus,
        System.currentTimeMillis(),
    )
    val utilityListState = rememberLazyListState()
    LaunchedEffect(backupExpanded) {
        if (backupExpanded) utilityListState.scrollToItem(3)
    }
    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherFiveMark()
            Text("DraftingRoom5", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        Text("Workspaces", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        HorizontalDivider()
        WorkspaceChoices.forEach { choice ->
            NavigationDrawerItem(
                label = { Text(choice.workspace.label) },
                selected = choice.workspace == active,
                icon = { Icon(choice.icon, contentDescription = null) },
                onClick = { onSelect(choice.workspace) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = utilityListState,
            verticalArrangement = Arrangement.Bottom,
        ) {
            item { HorizontalDivider() }
            item {
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("App updates")
                            Text(updatePresentation.summary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    },
                    selected = false,
                    icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                    badge = {
                        if (updatePresentation.actionLabel == "Working…") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(updatePresentation.actionLabel)
                    },
                    onClick = { if (updatePresentation.enabled) onUpdate() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            item {
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("Automatic backups")
                            Text(backupPresentation.summary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    },
                    selected = backupExpanded,
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    badge = { Switch(checked = backupStatus.enabled, onCheckedChange = onAutomaticBackupChange) },
                    onClick = { backupExpanded = !backupExpanded },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (backupExpanded) {
                item {
                    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
                        Text(
                            "Protects Fitness and Finance data. Health Connect data, permissions, and linked-account credentials are excluded.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        backupActionMessage?.let {
                            Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                        Button(onClick = onBackUpNow, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Back up now") }
                        OutlinedButton(
                            onClick = { confirmRestore = true },
                            enabled = backupStatus.hasRecoverySnapshot,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Restore latest") }
                        TextButton(onClick = onOpenBackupSettings, modifier = Modifier.align(Alignment.End)) { Text("Android backup settings") }
                    }
                }
            }
        }
    }
}

internal fun Modifier.workspaceLogoSemantics(active: AppWorkspace): Modifier = semantics {
    role = Role.Button
    contentDescription = "DraftingRoom5. Current workspace: ${active.label}. Open workspace drawer"
}
