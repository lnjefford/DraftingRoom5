package dev.draftingroom5

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    LauncherFiveMark()
                    Text("DraftingRoom5", fontWeight = FontWeight.Bold)
                    Text("Workspaces")
                }
                HorizontalDivider()
                WorkspaceChoices.forEach { choice ->
                    NavigationDrawerItem(
                        label = { Text(choice.workspace.label) },
                        selected = choice.workspace == active,
                        icon = { Icon(choice.icon, contentDescription = null) },
                        onClick = { closeAndSelect(choice.workspace) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        },
    ) {
        content { scope.launch { drawerState.open() } }
    }
}

internal fun Modifier.workspaceLogoSemantics(active: AppWorkspace): Modifier = semantics {
    role = Role.Button
    contentDescription = "DraftingRoom5. Current workspace: ${active.label}. Open workspace drawer"
}
