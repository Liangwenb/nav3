package com.liangwenb.feature.sample

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.liangwenb.nav.route.NavType
import com.liangwenb.nav.route.Route
import kotlinx.serialization.Serializable

@Serializable
data class FeatureSample(val message: String) : NavKey

@Serializable
data object FeatureSheet : NavKey

@Route(
    key = FeatureSample::class,
    route = "feature/{message}",
)
@Composable
fun FeatureSamplePage(key: FeatureSample) {
    Text(key.message)
}

@Route(
    key = FeatureSheet::class,
    type = NavType.BottomDialog,
    route = "dialog/feature-sheet",
)
@Composable
fun FeatureSheetPage() {
    Text("Feature sheet")
}
