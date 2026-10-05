package eu.hxreborn.pixelinjector.ui.util

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.verticalScrollbar(state: LazyListState): Modifier =
    state.scrollIndicatorState?.let { nonInteractiveScrollbar(it, Orientation.Vertical) } ?: this
