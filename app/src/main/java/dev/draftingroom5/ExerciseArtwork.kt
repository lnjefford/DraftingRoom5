package dev.draftingroom5

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

internal enum class ExerciseArtworkCrop { LIST, HEADER }

internal data class ExerciseArtworkAsset(
    val storageId: String,
    @StringRes val displayNameRes: Int,
    @DrawableRes val listAsset: Int,
    @DrawableRes val headerAsset: Int,
) {
    @DrawableRes
    fun resource(crop: ExerciseArtworkCrop): Int = when (crop) {
        ExerciseArtworkCrop.LIST -> listAsset
        ExerciseArtworkCrop.HEADER -> headerAsset
    }
}

internal object ExerciseArtworkCatalog {
    const val FALLBACK_ID = "generic"

    val entries: List<ExerciseArtworkAsset> = listOf(
        asset(FALLBACK_ID, R.string.exercise_artwork_generic, R.drawable.exercise_generic_list, R.drawable.exercise_generic_header),
        asset("dead_hang", R.string.exercise_artwork_dead_hang, R.drawable.exercise_dead_hang_list, R.drawable.exercise_dead_hang_header),
        asset("farmers_walk", R.string.exercise_artwork_farmers_walk, R.drawable.exercise_farmers_walk_list, R.drawable.exercise_farmers_walk_header),
        asset("grip_hold", R.string.exercise_artwork_grip_hold, R.drawable.exercise_grip_hold_list, R.drawable.exercise_grip_hold_header),
        asset("wrist_curl", R.string.exercise_artwork_wrist_curl, R.drawable.exercise_wrist_curl_list, R.drawable.exercise_wrist_curl_header),
        asset("reverse_wrist_curl", R.string.exercise_artwork_reverse_wrist_curl, R.drawable.exercise_reverse_wrist_curl_list, R.drawable.exercise_reverse_wrist_curl_header),
        asset("finger_extension", R.string.exercise_artwork_finger_extension, R.drawable.exercise_finger_extension_list, R.drawable.exercise_finger_extension_header),
        asset("wrist_rotation", R.string.exercise_artwork_wrist_rotation, R.drawable.exercise_wrist_rotation_list, R.drawable.exercise_wrist_rotation_header),
    )

    private val byId = entries.associateBy(ExerciseArtworkAsset::storageId)

    fun resolve(storageId: String?): ExerciseArtworkAsset = byId[storageId] ?: checkNotNull(byId[FALLBACK_ID])
}

private fun asset(
    storageId: String,
    @StringRes displayNameRes: Int,
    @DrawableRes listAsset: Int,
    @DrawableRes headerAsset: Int,
) = ExerciseArtworkAsset(storageId, displayNameRes, listAsset, headerAsset)
