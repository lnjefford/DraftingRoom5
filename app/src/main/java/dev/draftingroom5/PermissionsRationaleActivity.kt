package dev.draftingroom5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { DraftingRoom5Theme { HealthPrivacyScreen(::finish) } }
    }
}

@Composable
internal fun HealthPrivacyScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Health data privacy", onDone) },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "DraftingRoom5 reads weight, body fat, lean body mass, exercise sessions, and distance to display your fitness dashboard. Past-data access lets it find older Withings measurements outside Health Connect's standard history window.",
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Health data is read on this device and is not uploaded or shared. Recovery snapshots exclude Health Connect data and permissions. You can revoke access at any time in Health Connect settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppActionPill("Done", onDone, Modifier.fillMaxWidth())
        }
    }
}
