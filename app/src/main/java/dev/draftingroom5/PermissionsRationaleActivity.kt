package dev.draftingroom5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
        setContent {
            DraftingRoom5Theme {
                Surface(
                    Modifier.fillMaxSize().appScreenBackground(),
                    color = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier.systemBarsPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BrandTitle("DraftingRoom5")
                        SectionHeader(
                            title = "Health data privacy",
                            subtitle = "Clear, local, and under your control.",
                            eyebrow = "Your data",
                        )
                        BrandedCard(containerColor = AppSurfaceRaised) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text("DraftingRoom5 reads weight, body fat, lean body mass, exercise sessions, and distance to display your fitness dashboard. Past-data access lets it find older Withings measurements outside Health Connect's standard history window. Health data is read on this device and is not uploaded or shared. You can revoke access at any time in Health Connect settings.")
                                Button(onClick = { finish() }) { Text("Done") }
                            }
                        }
                    }
                }
            }
        }
    }
}
