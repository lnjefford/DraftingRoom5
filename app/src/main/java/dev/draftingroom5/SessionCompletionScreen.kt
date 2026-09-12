package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal data class SessionCompletionPresentation(
    val routineName: String,
    val exerciseProgress: String,
    val savedStatus: String,
)

internal fun sessionCompletionPresentation(history: WorkoutHistoryEntry) = SessionCompletionPresentation(
    routineName = history.snapshot.name,
    exerciseProgress = "${history.snapshot.exercises.size} of ${history.snapshot.exercises.size}",
    savedStatus = "Progress saved",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionCompletionScreen(history: WorkoutHistoryEntry, onReturnToDashboard: () -> Unit) {
    BackHandler(onBack = onReturnToDashboard)
    val presentation = sessionCompletionPresentation(history)
    val artwork = RoutineArtworkCatalog.resolve(history.snapshot.artworkId)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Session complete") },
                navigationIcon = {
                    IconButton(onClick = onReturnToDashboard, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "Return to dashboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GUIDED ROUTINE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(16.dp))
                    HorizontalDivider(Modifier.width(72.dp), thickness = 2.dp, color = AppGold)
                }
            }
            item {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Routine complete",
                    tint = AppMint,
                    modifier = Modifier.size(92.dp),
                )
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Routine complete",
                        color = Color(0xFFF4F0E7),
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        presentation.routineName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(MaterialTheme.shapes.large)) {
                    Image(
                        painter = painterResource(artwork.headerAsset),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, AppBackgroundDeep.copy(alpha = .72f)))))
                }
            }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth().semantics {
                    contentDescription = "Exercises ${presentation.exerciseProgress}. Sets all complete."
                }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CompletionFact("Exercises", presentation.exerciseProgress)
                        HorizontalDivider(color = AppBorder)
                        CompletionFact("Sets", "All complete")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = AppMint)
                    Text(presentation.savedStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Button(
                    onClick = onReturnToDashboard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep),
                ) { Text("Return to dashboard") }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun CompletionFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(name = "Session completion compact", widthDp = 320, heightDp = 800)
@Composable
private fun SessionCompletionCompactPreview() {
    SessionCompletionReviewPreview()
}

@Composable
internal fun SessionCompletionReviewPreview() {
    val routine = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }
    DraftingRoom5Theme {
        SessionCompletionScreen(
            WorkoutHistoryEntry(
                "preview-complete",
                OccurrenceKey("schedule-forearm", java.time.LocalDate.of(2026, 9, 12)),
                routine,
                1,
                2,
            ),
            {},
        )
    }
}
