package dev.draftingroom5.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText

class StandScheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StandScheduleSettings() }
    }
}

private val Mint = Color(0xFF69E6CA)
private val Ivory = Color(0xFFF6F6F6)
private val Muted = Color(0xFF9AADB2)
private val Days = listOf("M", "T", "W", "T", "F", "S", "S")
private val DayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
private fun Label(text: String, size: Int = 12, color: Color = Ivory) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.SemiBold))
}

@Composable
private fun StandScheduleSettings() {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf(readStandSchedule(context)) }
    fun change(next: StandSchedule) { schedule = next; saveStandSchedule(context, next) }
    Column(Modifier.fillMaxSize().background(Color(0xFF05090B)).verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("STAND HOURS", 15)
        Spacer(Modifier.height(8.dp))
        Label("Reminder days", 11, Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Days.forEachIndexed { index, day ->
                val enabled = schedule.days and (1 shl index) != 0
                androidx.compose.foundation.layout.Box(
                    Modifier.size(21.dp).background(if (enabled) Mint else Color(0xFF223235), CircleShape)
                        .clickable(role = Role.Checkbox) { change(schedule.copy(days = schedule.days xor (1 shl index))) }
                        .semantics { contentDescription = "${DayNames[index]} ${if (enabled) "on" else "off"}" },
                    contentAlignment = Alignment.Center
                ) { Label(day, 11, if (enabled) Color.Black else Muted) }
            }
        }
        Spacer(Modifier.height(9.dp))
        HourRow("Start", schedule.startHour, canDecrease = schedule.startHour > 0,
            canIncrease = schedule.startHour < schedule.endHour - 1,
            onDecrease = { change(schedule.copy(startHour = schedule.startHour - 1)) },
            onIncrease = { change(schedule.copy(startHour = schedule.startHour + 1)) })
        HourRow("End", schedule.endHour, canDecrease = schedule.endHour > schedule.startHour + 1,
            canIncrease = schedule.endHour < 24,
            onDecrease = { change(schedule.copy(endHour = schedule.endHour - 1)) },
            onIncrease = { change(schedule.copy(endHour = schedule.endHour + 1)) })
        Spacer(Modifier.height(4.dp))
        Label("Local watch time · end exclusive", 9, Muted)
    }
}

@Composable
private fun HourRow(label: String, hour: Int, canDecrease: Boolean, canIncrease: Boolean,
    onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Label(label, 12, Muted)
        androidx.compose.foundation.layout.Box(Modifier.size(21.dp)
            .clickable(enabled = canDecrease, role = Role.Button, onClick = onDecrease)
            .semantics { contentDescription = "Earlier $label" }, contentAlignment = Alignment.Center) {
            Label("−", 20, if (canDecrease) Mint else Muted)
        }
        Label("%02d:00".format(hour), 13)
        androidx.compose.foundation.layout.Box(Modifier.size(21.dp)
            .clickable(enabled = canIncrease, role = Role.Button, onClick = onIncrease)
            .semantics { contentDescription = "Later $label" }, contentAlignment = Alignment.Center) {
            Label("+", 18, if (canIncrease) Mint else Muted)
        }
    }
}
