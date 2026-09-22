package dev.draftingroom5

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.ui.IntegratedLoadState
import dev.draftingroom5.retirement.ui.RetirementIntegratedPage
import dev.draftingroom5.retirement.ui.retirementIntegratedPreviewState

class RetirementIntegratedCases : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Overview ready",
        "Overview attention",
        "Overview empty",
        "Assets",
        "Library",
        "Loading",
        "Error",
    )
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun RetirementIntegratedScreenshots(@PreviewParameter(RetirementIntegratedCases::class) screen: String) {
    val route = when (screen) {
        "Assets" -> AppRoute.RetirementAssets
        "Library" -> AppRoute.RetirementLibrary
        else -> AppRoute.RetirementOverview
    }
    val state = when (screen) {
        "Overview empty", "Loading", "Error" -> RetirementState()
        "Overview attention" -> retirementIntegratedPreviewState(attention = true)
        else -> retirementIntegratedPreviewState()
    }
    val loadState = when (screen) {
        "Loading" -> IntegratedLoadState.LOADING
        "Error" -> IntegratedLoadState.ERROR
        else -> IntegratedLoadState.READY
    }
    RetirementTheme {
        Surface(Modifier.fillMaxSize(), color = RetirementBackground, contentColor = RetirementText) {
            RetirementIntegratedPage(route, state, loadState)
        }
    }
}
