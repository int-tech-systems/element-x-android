/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.emoji.impl.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.emojibasebindings.Emoji
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.toSp
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.SearchBar
import io.element.android.libraries.emoji.impl.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch

@Composable
internal fun EmojiPickerView(
    state: DefaultEmojiPickerState,
    onSelectEmoji: (Emoji) -> Unit,
    selectedEmojis: ImmutableSet<String>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentDescription: @Composable (emoji: Emoji, isSelected: Boolean) -> String = { emoji, _ -> emoji.unicode },
) {
    val coroutineScope = rememberCoroutineScope()
    val grid = if (compact) CompactEmojiGrid else DefaultEmojiGrid
    val pagerState = rememberPagerState(pageCount = { state.categories.size })
    var skinPickerEmoji by remember { mutableStateOf<Emoji?>(null) }

    Column(modifier) {
        SearchBar(
            modifier = Modifier.padding(bottom = if (state.isSearchActive) 0.dp else 10.dp),
            queryState = state.searchQuery,
            resultState = state.searchResults,
            active = state.isSearchActive,
            onActiveChange = { state.eventSink(EmojiPickerEvent.ToggleSearchActive(it)) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            placeHolderTitle = stringResource(R.string.emoji_picker_search_placeholder),
        ) { emojis ->
            EmojiResults(
                emojis = emojis,
                isEmojiSelected = { selectedEmojis.contains(it.unicode) },
                onSelectEmoji = onSelectEmoji,
                onLongPress = { skinPickerEmoji = it },
                skinPickerEmoji = skinPickerEmoji,
                onDismissSkinPicker = { skinPickerEmoji = null },
                selectedEmojis = selectedEmojis,
                grid = grid,
                contentDescription = contentDescription,
            )
        }

        if (!state.isSearchActive) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                state.categories.forEachIndexed { index, category ->
                    Tab(
                        icon = {
                            when (category.icon) {
                                is IconSource.Resource -> Icon(
                                    resourceId = category.icon.id,
                                    contentDescription = stringResource(id = category.titleId)
                                )
                                is IconSource.Vector -> Icon(
                                    imageVector = category.icon.vector,
                                    contentDescription = stringResource(id = category.titleId)
                                )
                            }
                        },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                val emojis = state.categories[index].emojis
                EmojiResults(
                    emojis = emojis,
                    isEmojiSelected = { selectedEmojis.contains(it.unicode) },
                    onSelectEmoji = onSelectEmoji,
                    onLongPress = { skinPickerEmoji = it },
                    skinPickerEmoji = skinPickerEmoji,
                    onDismissSkinPicker = { skinPickerEmoji = null },
                    selectedEmojis = selectedEmojis,
                    grid = grid,
                    contentDescription = contentDescription,
                )
            }
        }
    }
}

/**
 * Geometry defining each cell of the emoji grid.
 */
private data class EmojiGrid(
    val cellSize: Dp,
    val emojiSize: Dp,
    val horizontalSpacing: Dp,
    val verticalSpacing: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
)

/** Reaction picker and status picker: 6 columns when the screen is 393 dp wide. */
private val DefaultEmojiGrid = EmojiGrid(
    cellSize = 48.dp,
    emojiSize = 32.dp,
    horizontalSpacing = 8.dp,
    verticalSpacing = 2.dp,
    horizontalPadding = 16.dp,
    verticalPadding = 10.dp,
)

/**
 * Composer picker: 9 columns when the screen is 393 dp wide, roughly matching keyboard layouts. The composer already applies 16 dp
 * of padding on each side of the picker, so the grid carries no horizontal padding of its own.
 */
private val CompactEmojiGrid = EmojiGrid(
    cellSize = 40.dp,
    emojiSize = 26.dp,
    horizontalSpacing = 0.dp,
    verticalSpacing = 0.dp,
    horizontalPadding = 0.dp,
    verticalPadding = 4.dp,
)

@Composable
private fun EmojiResults(
    emojis: ImmutableList<Emoji>,
    isEmojiSelected: (Emoji) -> Boolean,
    onSelectEmoji: (Emoji) -> Unit,
    onLongPress: (Emoji) -> Unit,
    skinPickerEmoji: Emoji?,
    onDismissSkinPicker: () -> Unit,
    selectedEmojis: ImmutableSet<String>,
    grid: EmojiGrid,
    contentDescription: @Composable (emoji: Emoji, isSelected: Boolean) -> String,
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = grid.cellSize),
        contentPadding = PaddingValues(vertical = grid.verticalPadding, horizontal = grid.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(grid.horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(grid.verticalSpacing)
    ) {
        items(emojis, key = { it.unicode }) { item ->
            EmojiItem(
                modifier = Modifier.aspectRatio(1f),
                item = item,
                isSelected = isEmojiSelected(item),
                onSelectEmoji = onSelectEmoji,
                onLongPress = onLongPress,
                skinPickerEmoji = skinPickerEmoji,
                onDismissSkinPicker = onDismissSkinPicker,
                emojiSize = grid.emojiSize.toSp(),
                selectedSkinUnicodes = selectedEmojis,
                hasSelectedSkin = item.skins?.any { skin -> skin.unicode in selectedEmojis } == true,
                contentDescription = contentDescription,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun EmojiPickerViewPreview(@PreviewParameter(DefaultEmojiPickerStatePreviewParam::class) state: DefaultEmojiPickerState) = ElementPreview {
    EmojiPickerView(
        state = state,
        onSelectEmoji = {},
        selectedEmojis = persistentSetOf("😀", "😄", "😃"),
        modifier = Modifier.fillMaxWidth(),
    )
}
