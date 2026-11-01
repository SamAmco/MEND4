/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */
package co.samco.mendroid.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import co.samco.mendroid.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Date fast scroller adapted from Track & Graph's DateScrollLazyColumn. */
@Composable
fun DateScrollLazyColumn(
    state: LazyListState,
    itemCount: Int,
    dateLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    var height by remember { mutableIntStateOf(0) }
    var dragging by remember(itemCount) { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var dragMaxIndex by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember(itemCount) { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    val handleSize = 48.dp
    val handleHeight = with(LocalDensity.current) { handleSize.toPx() }
    val travel = (height - handleHeight).coerceAtLeast(0f)
    val canScroll by remember(state) {
        derivedStateOf { state.canScrollForward || state.canScrollBackward }
    }
    // Estimate the scrollable range in items so the handle reaches the bottom even when
    // several entries fit in the viewport. Entry heights may differ considerably.
    val maxIndex by remember(state, itemCount) {
        derivedStateOf {
            val layout = state.layoutInfo
            val averageHeight = layout.visibleItemsInfo.map { it.size }.average()
            if (averageHeight.isNaN() || averageHeight <= 0) 0f
            else (itemCount - (layout.viewportEndOffset - layout.viewportStartOffset) /
                averageHeight).toFloat().coerceIn(0f, (itemCount - 1).coerceAtLeast(0).toFloat())
        }
    }
    val progress by remember(state, maxIndex) {
        derivedStateOf {
            when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                maxIndex <= 0 -> 0f
                else -> {
                    val firstHeight = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                    ((state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset /
                        firstHeight.coerceAtLeast(1).toFloat()) / maxIndex).coerceIn(0f, 1f)
                }
            }
        }
    }

    LaunchedEffect(state.isScrollInProgress, dragging, canScroll) {
        if (canScroll && (state.isScrollInProgress || dragging)) visible = true
        else {
            delay(2000)
            visible = false
        }
    }

    fun scrollTo(fraction: Float, range: Float) {
        targetIndex = dateScrollIndex(fraction, range, itemCount)
        state.requestScrollToItem(targetIndex)
    }

    Box(modifier.onSizeChanged { height = it.height }) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = state, content = content)

        DisableSelection {
            AnimatedVisibility(
                visible = dragging,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = dateLabel(targetIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            val description = stringResource(R.string.scroll_by_date)
            AnimatedVisibility(
                visible = visible && canScroll && itemCount >= 50 && travel > 0,
                modifier = Modifier.align(Alignment.TopEnd).offset {
                    IntOffset(0, ((if (dragging) dragProgress else progress) * travel).roundToInt())
                },
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .size(handleSize)
                        .semantics {
                            contentDescription = description
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                            setProgress { requested ->
                                scrollTo(requested.coerceIn(0f, 1f), maxIndex)
                                true
                            }
                        }
                        .pointerInput(state, itemCount, travel) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragProgress = progress
                                    dragMaxIndex = maxIndex
                                    targetIndex = state.firstVisibleItemIndex
                                    dragging = true
                                },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragProgress = (dragProgress + amount / travel).coerceIn(0f, 1f)
                                    scrollTo(dragProgress, dragMaxIndex)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false }
                            )
                        },
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DragHandle, contentDescription = null)
                    }
                }
            }
        }
    }
}

internal fun dateScrollIndex(progress: Float, maxIndex: Float, itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val fraction = progress.coerceIn(0f, 1f)
    // Request the last item at the bottom so it is fully visible, including tall entries.
    if (fraction == 1f) return itemCount - 1
    return (fraction * maxIndex).roundToInt().coerceIn(0, itemCount - 1)
}
