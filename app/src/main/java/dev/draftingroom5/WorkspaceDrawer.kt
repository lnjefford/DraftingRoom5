package dev.draftingroom5

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
    fun closeAndUpdate() {
        scope.launch {
            drawerState.close()
            onUpdate()
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(304.dp).fillMaxHeight()) {
                WorkspaceDrawerContent(active, updatePresentation, ::closeAndSelect, ::closeAndUpdate)
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
) {
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
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
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
}

internal fun Modifier.workspaceLogoSemantics(active: AppWorkspace): Modifier = semantics {
    role = Role.Button
    contentDescription = "DraftingRoom5. Current workspace: ${active.label}. Open workspace drawer"
}
