package dev.draftingroom5

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

internal enum class RoutineArtworkCrop { CARD, HEADER, PICKER }

internal data class RoutineArtworkAsset(
    val storageId: String,
    @StringRes val displayNameRes: Int,
    @DrawableRes val cardAsset: Int,
    @DrawableRes val headerAsset: Int,
    @DrawableRes val pickerAsset: Int,
) {
    @DrawableRes
    fun resource(crop: RoutineArtworkCrop): Int = when (crop) {
        RoutineArtworkCrop.CARD -> cardAsset
        RoutineArtworkCrop.HEADER -> headerAsset
        RoutineArtworkCrop.PICKER -> pickerAsset
    }
}

internal object RoutineArtworkCatalog {
    const val FALLBACK_ID = "generic"

    val entries: List<RoutineArtworkAsset> = listOf(
        asset(FALLBACK_ID, R.string.routine_artwork_generic, R.drawable.routine_generic_card, R.drawable.routine_generic_header, R.drawable.routine_generic_picker),
        asset("dumbbell", R.string.routine_artwork_dumbbell, R.drawable.routine_dumbbell_card, R.drawable.routine_dumbbell_header, R.drawable.routine_dumbbell_picker),
        asset("grip_trainer", R.string.routine_artwork_grip_trainer, R.drawable.routine_grip_trainer_card, R.drawable.routine_grip_trainer_header, R.drawable.routine_grip_trainer_picker),
        asset("running_shoe", R.string.routine_artwork_running_shoe, R.drawable.routine_running_shoe_card, R.drawable.routine_running_shoe_header, R.drawable.routine_running_shoe_picker),
        asset("kettlebell", R.string.routine_artwork_kettlebell, R.drawable.routine_kettlebell_card, R.drawable.routine_kettlebell_header, R.drawable.routine_kettlebell_picker),
        asset("leg_day", R.string.routine_artwork_leg_day, R.drawable.routine_leg_day_card, R.drawable.routine_leg_day_header, R.drawable.routine_leg_day_picker),
        asset("full_body", R.string.routine_artwork_full_body, R.drawable.routine_full_body_card, R.drawable.routine_full_body_header, R.drawable.routine_full_body_picker),
        asset("push_day", R.string.routine_artwork_push_day, R.drawable.routine_push_day_card, R.drawable.routine_push_day_header, R.drawable.routine_push_day_picker),
        asset("pull_day", R.string.routine_artwork_pull_day, R.drawable.routine_pull_day_card, R.drawable.routine_pull_day_header, R.drawable.routine_pull_day_picker),
        asset("jump_rope", R.string.routine_artwork_jump_rope, R.drawable.routine_jump_rope_card, R.drawable.routine_jump_rope_header, R.drawable.routine_jump_rope_picker),
        asset("stopwatch", R.string.routine_artwork_stopwatch, R.drawable.routine_stopwatch_card, R.drawable.routine_stopwatch_header, R.drawable.routine_stopwatch_picker),
    )

    private val byId = entries.associateBy(RoutineArtworkAsset::storageId)

    fun resolve(storageId: String?): RoutineArtworkAsset = byId[storageId] ?: checkNotNull(byId[FALLBACK_ID])
}

private fun asset(
    storageId: String,
    @StringRes displayNameRes: Int,
    @DrawableRes cardAsset: Int,
    @DrawableRes headerAsset: Int,
    @DrawableRes pickerAsset: Int,
) = RoutineArtworkAsset(storageId, displayNameRes, cardAsset, headerAsset, pickerAsset)
