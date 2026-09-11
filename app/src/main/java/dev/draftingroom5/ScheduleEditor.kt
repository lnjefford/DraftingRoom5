package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal data class ScheduleEditorDraft(
    val routineId: String?,
    val days: Set<DayOfWeek>,
    val repeat: ScheduleRepeat,
    val anchor: DayOfWeek,
) {
    fun selectRoutine(id: String) = copy(routineId = id)

    fun applyRepeat(value: ScheduleRepeat): ScheduleEditorDraft = copy(
        days = repeatDays(value, anchor, days),
        repeat = value,
    )

    fun toggleDay(day: DayOfWeek): ScheduleEditorDraft {
        val updated = if (day in days) days - day else days + day
        return copy(days = updated, repeat = ScheduleRepeat.CUSTOM, anchor = day)
    }

    fun toEntry(originalId: String?, newEntryId: String, routines: List<Routine>): ScheduleEntry? {
        val id = routineId?.takeIf { candidate -> routines.any { it.id == candidate } } ?: return null
        if (days.isEmpty()) return null
        return ScheduleEntry(originalId ?: newEntryId, id, days)
    }

    companion object {
        fun initial(original: ScheduleEntry?, routines: List<Routine>, requestedAnchor: DayOfWeek): ScheduleEditorDraft {
            val days = original?.days ?: setOf(requestedAnchor)
            return ScheduleEditorDraft(
                routineId = original?.routineId ?: routines.firstOrNull()?.id,
                days = days,
                repeat = repeatPattern(days),
                anchor = original?.days?.minByOrNull(DayOfWeek::getValue) ?: requestedAnchor,
            )
        }
    }
}

internal fun TrainingPlan.saveScheduleDraft(
    originalId: String?,
    draft: ScheduleEditorDraft,
    newEntryId: String,
): TrainingPlan? {
    val entry = draft.toEntry(originalId, newEntryId, routines) ?: return null
    if (originalId == null) return copy(schedule = schedule + entry)
    if (schedule.none { it.id == originalId }) return null
    return copy(schedule = schedule.map { if (it.id == originalId) entry else it })
}

internal fun Set<DayOfWeek>.recurrenceDescription(locale: Locale = Locale.getDefault()): String {
    if (isEmpty()) return "Choose at least one day"
    return when (repeatPattern(this)) {
        ScheduleRepeat.DAILY -> "Every day"
        ScheduleRepeat.WEEKDAYS -> "Every weekday"
        ScheduleRepeat.WEEKLY -> "Every ${single().getDisplayName(TextStyle.FULL, locale)}"
        ScheduleRepeat.CUSTOM -> "Every " + sortedBy(DayOfWeek::getValue).joinToString(", ") {
            it.getDisplayName(TextStyle.FULL, locale)
        }
    }
}

@Composable
internal fun ScheduleEditorScreen(
    plan: TrainingPlan,
    original: ScheduleEntry?,
    draftId: String,
    requestedAnchor: DayOfWeek,
    hasSavedSessions: Boolean = false,
    onSave: (TrainingPlan) -> Boolean,
    onBack: () -> Unit,
) {
    val initial = remember(original?.id, draftId, plan.routines) {
        ScheduleEditorDraft.initial(original, plan.routines, requestedAnchor)
    }
    var routineId by rememberSaveable(original?.id, draftId) { mutableStateOf(initial.routineId) }
    var dayNames by rememberSaveable(original?.id, draftId) { mutableStateOf(initial.days.map { it.name }) }
    var repeatName by rememberSaveable(original?.id, draftId) { mutableStateOf(initial.repeat.name) }
    var anchorName by rememberSaveable(original?.id, draftId) { mutableStateOf(initial.anchor.name) }
    var confirmDiscard by rememberSaveable(original?.id, draftId) { mutableStateOf(false) }
    var saveFailed by rememberSaveable(original?.id, draftId) { mutableStateOf(false) }
    var confirmReassignment by rememberSaveable(original?.id, draftId) { mutableStateOf(false) }
    val currentIdentity = original?.let { listOf(it.id, it.routineId) + it.days.sortedBy(DayOfWeek::getValue).map { day -> day.name } }.orEmpty()
    val originalIdentity = rememberSaveable(original?.id, draftId) { currentIdentity }
    val stale = originalIdentity != currentIdentity

    fun currentDraft() = ScheduleEditorDraft(
        routineId = routineId,
        days = dayNames.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet(),
        repeat = runCatching { ScheduleRepeat.valueOf(repeatName) }.getOrDefault(ScheduleRepeat.CUSTOM),
        anchor = runCatching { DayOfWeek.valueOf(anchorName) }.getOrDefault(requestedAnchor),
    )
    fun requestBack() {
        if (currentDraft() == initial) onBack() else confirmDiscard = true
    }

    BackHandler(onBack = ::requestBack)
    val draft = currentDraft()
    val valid = !stale && draft.toEntry(original?.id, draftId, plan.routines) != null
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(if (original == null) "Add scheduled item" else "Edit scheduled item", ::requestBack) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                EditorialHeading(
                    eyebrow = "Routine",
                    title = if (original == null) "Add to your week" else "Update recurrence",
                    supportingText = "Choose an existing routine, then set the days it repeats.",
                )
            }
            if (plan.routines.isEmpty()) {
                item {
                    AppSurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No routines available", fontWeight = FontWeight.Bold)
                            Text("Create a guided or linked-app routine before adding it to the schedule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(plan.routines, key = { it.id }) { routine ->
                    RoutineChoiceRow(routine, selected = routine.id == draft.routineId) { routineId = routine.id }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("REPEAT", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScheduleRepeat.entries) { option ->
                            FilterChip(
                                selected = draft.repeat == option,
                                onClick = {
                                    val updated = draft.applyRepeat(option)
                                    dayNames = updated.days.map { it.name }
                                    repeatName = updated.repeat.name
                                },
                                label = { Text(option.editorLabel()) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DAYS", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(DayOfWeek.entries) { day ->
                            FilterChip(
                                selected = day in draft.days,
                                onClick = {
                                    val updated = draft.toggleDay(day)
                                    dayNames = updated.days.map { it.name }
                                    repeatName = updated.repeat.name
                                    anchorName = updated.anchor.name
                                },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    contentDescription = "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${if (day in draft.days) "included" else "not included"}"
                                },
                            )
                        }
                    }
                }
            }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RECURRENCE", color = AppMint, style = MaterialTheme.typography.labelMedium)
                        Text(draft.days.recurrenceDescription(), style = MaterialTheme.typography.titleLarge)
                        Text("The weekday selection above is the saved schedule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (saveFailed) item { Text("Couldn’t save these changes. Try again.", color = MaterialTheme.colorScheme.error) }
            if (stale) item { Text("This scheduled item changed elsewhere. Go back and reopen it to review the current values.", color = MaterialTheme.colorScheme.error) }
            item {
                Button(
                    onClick = {
                        if (hasSavedSessions && original != null && draft.routineId != original.routineId) confirmReassignment = true
                        else {
                            val updated = plan.saveScheduleDraft(original?.id, draft, draftId)
                            if (updated != null && onSave(updated)) onBack() else saveFailed = true
                        }
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (original == null) "Add to schedule" else "Save changes") }
            }
        }
    }
    if (confirmReassignment) AppConfirmationDialog(
        title = "Change the scheduled routine?",
        message = "Saved incomplete sessions still use the original routine and progress. The new routine applies to future sessions.",
        confirmLabel = "Save changes",
        onConfirm = {
            val updated = plan.saveScheduleDraft(original?.id, draft, draftId)
            if (!stale && updated != null && onSave(updated)) onBack() else saveFailed = true
            confirmReassignment = false
        },
        onDismiss = { confirmReassignment = false },
    )
    if (confirmDiscard) AppConfirmationDialog(
        title = "Discard schedule changes?",
        message = "Your routine and weekday changes haven’t been saved.",
        confirmLabel = "Discard",
        onConfirm = onBack,
        onDismiss = { confirmDiscard = false },
    )
}

@Composable
private fun RoutineChoiceRow(routine: Routine, selected: Boolean, onSelect: () -> Unit) {
    AppSurfaceCard(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).semantics { role = Role.RadioButton },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(RoutineArtworkCatalog.resolve(routine.artworkId).pickerAsset),
                contentDescription = null,
                modifier = Modifier.size(60.dp).clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(routine.name, fontWeight = FontWeight.Bold)
                Text(routine.scheduleEditorMetadata(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun Routine.scheduleEditorMetadata(): String = when (execution) {
    RoutineExecution.GUIDED -> "Guided routine · ${if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises"}"
    RoutineExecution.LINKED_APP -> "Linked app · ${linkedAppDisplayName(checkNotNull(appLink).packageName)}"
}

private fun ScheduleRepeat.editorLabel(): String = when (this) {
    ScheduleRepeat.WEEKLY -> "Weekly"
    ScheduleRepeat.WEEKDAYS -> "Weekdays"
    ScheduleRepeat.DAILY -> "Every day"
    ScheduleRepeat.CUSTOM -> "Custom"
}
