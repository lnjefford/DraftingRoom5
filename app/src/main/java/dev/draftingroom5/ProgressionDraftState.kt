package dev.draftingroom5

import androidx.compose.runtime.saveable.Saver
import org.json.JSONArray
import org.json.JSONObject

// Drafts can have no steps yet, so restore their structure without applying final save validation.
internal val CustomProgressionStateSaver = Saver<CustomExerciseProgression, String>(
    save = { encodeProgression(it).toString() },
    restore = { decodeProgression(JSONObject(it)) as CustomExerciseProgression },
)

internal val NullableCustomProgressionStateSaver = Saver<CustomExerciseProgression?, String>(
    save = { it?.let { rule -> encodeProgression(rule).toString() } ?: "null" },
    restore = { if (it == "null") null else decodeProgression(JSONObject(it)) as CustomExerciseProgression },
)

internal val InsertedExerciseStateSaver = Saver<List<Exercise>, String>(
    save = { JSONArray().apply { it.forEach { exercise -> put(encodeExercise(exercise)) } }.toString() },
    restore = { text -> JSONArray(text).let { array -> List(array.length()) { decodeExercise(array.getJSONObject(it)) } } },
)
