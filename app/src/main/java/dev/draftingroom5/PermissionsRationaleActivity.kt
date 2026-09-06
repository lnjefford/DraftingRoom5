package dev.draftingroom5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.padding(24.dp)) {
                    Text("Health data privacy", style = MaterialTheme.typography.headlineMedium)
                    Text("DraftingRoom5 reads weight, body fat, lean body mass, exercise sessions, and distance to display your fitness dashboard. Health data is read on this device and is not uploaded or shared. You can revoke access at any time in Health Connect settings.")
                    Button(onClick = { finish() }) { Text("Done") }
                }
            }
        }
    }
}
