/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.emoji.impl.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun EmojiPickerView(
    state: DefaultEmojiPickerState,
    onSelectEmoji: (Emoji) -> Unit,
    selectedEmojis: ImmutableSet<String>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onBackspace: (() -> Unit)? = null,
    contentDescription: @Composable (emoji: Emoji, isSelected: Boolean) -> String = { emoji, _ -> emoji.unicode },
) {
    val coroutineScope = rememberCoroutineScope()
    val grid = if (compact) CompactEmojiGrid else DefaultEmojiGrid
    // The backspace key overlays the grid, which receives extra padding so its final rows can still scroll clear of it.
    val extraBottomPadding = if (onBackspace != null) BackspaceKeySize + BackspaceKeyMargin else 0.dp
    val pagerState = rememberPagerState(pageCount = { state.categories.size })
    var skinPickerEmoji by remember { mutableStateOf<Emoji?>(null) }

    Box(modifier) {
        Column {
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
                    extraBottomPadding = extraBottomPadding,
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
                        extraBottomPadding = extraBottomPadding,
                        contentDescription = contentDescription,
                    )
                }
            }
        }
        if (onBackspace != null) {
            BackspaceKey(
                onBackspace = onBackspace,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(BackspaceKeyMargin),
            )
        }
    }
}

private val BackspaceKeySize = 44.dp
private val BackspaceKeyMargin = 12.dp
private val BackspaceRepeatInitialDelay = 400.milliseconds
private val BackspaceRepeatInterval = 50.milliseconds

/**
 * Backspace key floating above the grid while the picker stands in for the system keyboard; holding it repeats the
 * deletion exactly as a keyboard backspace does.
 */
@Composable
private fun BackspaceKey(
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnBackspace by rememberUpdatedState(onBackspace)
    val interactionSource = remember { MutableInteractionSource() }
    val description = stringResource(R.string.emoji_picker_a11y_backspace)
    Box(
        modifier = modifier
            .size(BackspaceKeySize)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick {
                    currentOnBackspace()
                    true
                }
            }
            .indication(interactionSource, ripple())
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        coroutineScope {
                            val repeater = launch {
                                currentOnBackspace()
                                delay(BackspaceRepeatInitialDelay)
                                while (isActive) {
                                    currentOnBackspace()
                                    delay(BackspaceRepeatInterval)
                                }
                            }
                            val released = tryAwaitRelease()
                            repeater.cancel()
                            interactionSource.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.BackspaceSolid(),
            contentDescription = null,
            tint = ElementTheme.colors.iconPrimary,
        )
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
    extraBottomPadding: Dp,
    contentDescription: @Composable (emoji: Emoji, isSelected: Boolean) -> String,
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = grid.cellSize),
        contentPadding = PaddingValues(
            start = grid.horizontalPadding,
            top = grid.verticalPadding,
            end = grid.horizontalPadding,
            bottom = grid.verticalPadding + extraBottomPadding,
        ),
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

@PreviewsDayNight
@Composable
internal fun EmojiPickerViewWithBackspacePreview(@PreviewParameter(
    DefaultEmojiPickerStatePreviewParam::class
) state: DefaultEmojiPickerState) = ElementPreview {
    EmojiPickerView(
        state = state,
        onSelectEmoji = {},
        selectedEmojis = persistentSetOf(),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        compact = true,
        onBackspace = {},
    )
}
