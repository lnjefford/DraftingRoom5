package dev.draftingroom5

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppDocumentCodecTest {
    @Test fun malformedJsonVariantsCannotUsePlatformParserExtensions() {
        val valid = encodeAppDocument(defaultAppDocument())
        listOf(valid + " garbage", valid + "{}", valid.replace('"', '\''),
            valid.replaceFirst("{", "{unquoted:1,"), valid.dropLast(1) + ",}",
            valid.replaceFirst("{", "{\"format\":\"x\",\"\\u0066ormat\":\"y\","),
            "[".repeat(33) + "0" + "]".repeat(33), "{", "{\"a\": [}")
            .forEach { malformed ->
                assertThrows(malformed.take(100), IllegalArgumentException::class.java) { decodeAppDocument(malformed) }
            }
    }

    @Test fun unknownCardStillRequiresBooleanVisibility() {
        val root = JSONObject(encodeAppDocument(defaultAppDocument()))
        root.getJSONObject("preferences").getJSONObject("dashboardLayout").getJSONArray("cards")
            .put(JSONObject().put("card", "FUTURE").put("visible", "true"))
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
    }

    @Test fun finiteDoubleVoiceRateClampsBeforeFloatConversion() {
        val root = JSONObject(encodeAppDocument(defaultAppDocument()))
        root.getJSONObject("preferences").getJSONObject("voice").put("rate", 1e100)
        assertEquals(1.5f, decodeAppDocument(root.toString()).preferences.voice.rate)
    }

    @Test fun currentDocumentRoundTripPreservesDefaultsAndIntegerSets() {
        val document = defaultAppDocument()
        val restored = decodeAppDocument(encodeAppDocument(document))
        assertEquals(document, restored)
        assertEquals(3, restored.plan.routines.single { it.id == "routine-forearm" }.exercises.first().setCount)
    }

    @Test fun releasedV025DocumentUpgradesWithoutLosingWorkoutData() {
        val current = progressionDocumentFixture()
        val legacy = JSONObject(encodeAppDocument(current)).asReleasedV025Document()

        val upgraded = decodeAppDocument(legacy.toString())
        val expected = current.copy(
            plan = current.plan.copy(routines = current.plan.routines.map(Routine::withoutProgression)),
            partialSessions = current.partialSessions.map { it.copy(
                snapshot = it.snapshot.withoutProgression(), handledProgressionExerciseIds = emptySet(),
            ) },
            history = current.history.map { it.copy(snapshot = it.snapshot.withoutProgression()) },
            progressionReceipts = emptyList(),
        )

        assertEquals(expected, upgraded)
        assertEquals(upgraded, decodeAppDocument(encodeAppDocument(upgraded)))
    }

    @Test fun previousSchemaUpgradeDoesNotAcceptHybridDocuments() {
        val hybrid = JSONObject(encodeAppDocument(defaultAppDocument())).apply { remove("progressionReceipts") }
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(hybrid.toString()) }
    }

    @Test fun stringSetCountIsRejectedRatherThanParsed() {
        val root = JSONObject(encodeAppDocument(defaultAppDocument()))
        root.getJSONObject("plan").getJSONArray("routines").getJSONObject(2)
            .getJSONArray("exercises").getJSONObject(0).put("setCount", "3")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
    }

    @Test fun progressionNumbersAreStrictAndNeverRecoveredFromTargetText() {
        val root = JSONObject(encodeAppDocument(progressionDocumentFixture()))
        val exercise = root.getJSONObject("plan").getJSONArray("routines").getJSONObject(2)
            .getJSONArray("exercises").getJSONObject(1)
        exercise.getJSONObject("measurements").put("weightPounds", "25 lb")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
    }

    @Test fun danglingScheduleReferenceIsCorruption() {
        val root = JSONObject(encodeAppDocument(defaultAppDocument()))
        root.getJSONObject("plan").getJSONArray("schedule").getJSONObject(0).put("routineId", "missing")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
    }

    @Test fun duplicateObjectKeysAreRejectedBeforeOrgJsonCanOverwriteThem() {
        val encoded = encodeAppDocument(defaultAppDocument())
        val duplicate = encoded.replaceFirst("\"format\":", "\"format\":\"draftingroom5.current\",\"format\":")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(duplicate) }
    }

    @Test fun currentLayoutNormalizationDropsUnknownCardsAndRepairsVisibility() {
        val root = JSONObject(encodeAppDocument(defaultAppDocument()))
        val cards = root.getJSONObject("preferences").getJSONObject("dashboardLayout").getJSONArray("cards")
        repeat(cards.length()) { cards.getJSONObject(it).put("visible", false) }
        cards.put(JSONObject().put("card", "FUTURE_CARD").put("visible", true))
        assertEquals(defaultDashboardCards(), decodeAppDocument(root.toString()).preferences.dashboardLayout.cards)
    }
}

private fun JSONObject.asReleasedV025Document(): JSONObject = apply {
    remove("progressionReceipts")
    getJSONObject("plan").getJSONArray("routines").forEachObject(::stripProgressionFields)
    getJSONArray("partialSessions").forEachObject { session ->
        session.remove("handledProgressionExerciseIds")
        stripProgressionFields(session.getJSONObject("snapshot"))
    }
    getJSONArray("history").forEachObject { history -> stripProgressionFields(history.getJSONObject("snapshot")) }
}

private fun stripProgressionFields(routine: JSONObject) {
    routine.getJSONArray("exercises").forEachObject { exercise ->
        exercise.remove("measurements")
        exercise.remove("progression")
    }
}

private fun Routine.withoutProgression() = copy(exercises = exercises.map {
    it.copy(measurements = ExerciseMeasurements(), progression = null)
})

private inline fun org.json.JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    repeat(length()) { block(getJSONObject(it)) }
}
