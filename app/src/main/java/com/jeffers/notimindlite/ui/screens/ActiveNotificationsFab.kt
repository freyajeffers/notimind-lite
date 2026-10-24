package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ActiveNotificationsFab(listState: LazyListState) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallFloatingActionButton(
            modifier = Modifier.semantics { contentDescription = "PageUpButton" },
            onClick = {
                val first = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()
                    ?.index ?: 0
                val page = listState.layoutInfo.visibleItemsInfo.size
                    .coerceAtLeast(1)
                scope.launch { listState.animateScrollToItem((first - page).coerceAtLeast(0)) }
            },
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Page up")
        }
        SmallFloatingActionButton(
            modifier = Modifier.semantics { contentDescription = "PageDownButton" },
            onClick = {
                val first = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()
                    ?.index ?: 0
                val page = listState.layoutInfo.visibleItemsInfo.size
                    .coerceAtLeast(1)
                scope.launch { listState.animateScrollToItem(first + page) }
            },
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Page down")
        }
    }
}
