package com.faruque.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faruque.player.R
import com.faruque.player.data.formatDuration
import com.faruque.player.data.formatFileSize
import com.faruque.player.model.LibraryUiState
import com.faruque.player.model.MediaFilter
import com.faruque.player.model.MediaFolder
import com.faruque.player.model.MediaItemModel
import com.faruque.player.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    uiState: LibraryUiState,
    selectedFolderName: String?,
    mediaFilter: MediaFilter,
    searchQuery: String,
    onOpenFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onSetFilter: (MediaFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlayItem: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onRemoveItem: (MediaItemModel) -> Unit,
    onRemoveFolder: (MediaFolder) -> Unit,
    onClearAll: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onPickGalleryVideos: () -> Unit
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    if (selectedFolderName != null) {
        BackHandler(onBack = onCloseFolder)
    }

    val contentState = uiState as? LibraryUiState.Content
    val activeFolder = contentState?.folders?.firstOrNull { it.name == selectedFolderName }
    val hasContent = contentState != null && contentState.allItems.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (activeFolder != null) {
                        IconButton(
                            onClick = onCloseFolder,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("folder_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_button_desc)
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = activeFolder?.name ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeFolder != null) {
                            Text(
                                text = stringResource(
                                    R.string.folder_summary,
                                    activeFolder.videoCount,
                                    activeFolder.audioCount
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (contentState != null) {
                            Text(
                                text = stringResource(R.string.folders_title) +
                                    " (${contentState.folders.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (hasContent) {
                        IconButton(
                            onClick = {
                                searchVisible = !searchVisible
                                if (!searchVisible) onSearchQueryChange("")
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("search_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_desc)
                            )
                        }
                        IconButton(
                            onClick = onPickFiles,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_add_files_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = stringResource(R.string.open_file_desc)
                            )
                        }
                        if (activeFolder == null) {
                            TextButton(
                                onClick = onClearAll,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_all_button")
                            ) {
                                Text(text = stringResource(R.string.clear_list_action))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (hasContent) {
                ExtendedFloatingActionButton(
                    onClick = onPickFolder,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = stringResource(R.string.add_folder_desc)
                        )
                    },
                    text = { Text(text = stringResource(R.string.add_folder_button)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_folder_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (uiState) {
                is LibraryUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading_videos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is LibraryUiState.Empty -> {
                    EmptyLibraryView(
                        onPickFolder = onPickFolder,
                        onPickFiles = onPickFiles,
                        onPickGalleryVideos = onPickGalleryVideos,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 560.dp)
                    )
                }

                is LibraryUiState.Error -> {
                    EmptyLibraryView(
                        onPickFolder = onPickFolder,
                        onPickFiles = onPickFiles,
                        onPickGalleryVideos = onPickGalleryVideos,
                        errorMessage = uiState.message,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 560.dp)
                    )
                }

                is LibraryUiState.Content -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp)
                    ) {
                        AnimatedVisibility(visible = searchVisible) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("search_input")
                            )
                        }

                        MediaFilterBar(
                            selected = mediaFilter,
                            allItems = activeFolder?.items ?: uiState.allItems,
                            onSelect = onSetFilter
                        )

                        if (activeFolder == null) {
                            FolderListContent(
                                folders = uiState.folders,
                                mediaFilter = mediaFilter,
                                searchQuery = searchQuery,
                                onOpenFolder = onOpenFolder,
                                onRemoveFolder = onRemoveFolder
                            )
                        } else {
                            FolderItemsContent(
                                items = activeFolder.items,
                                mediaFilter = mediaFilter,
                                searchQuery = searchQuery,
                                onPlayItem = onPlayItem,
                                onRemoveItem = onRemoveItem
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaFilterBar(
    selected: MediaFilter,
    allItems: List<MediaItemModel>,
    onSelect: (MediaFilter) -> Unit
) {
    val totalCount = allItems.size
    val videoCount = allItems.count { it.mediaType == MediaType.VIDEO }
    val audioCount = allItems.count { it.mediaType == MediaType.AUDIO }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == MediaFilter.ALL,
            onClick = { onSelect(MediaFilter.ALL) },
            label = { Text("${stringResource(R.string.filter_all)} ($totalCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.testTag("filter_all")
        )
        FilterChip(
            selected = selected == MediaFilter.VIDEO,
            onClick = { onSelect(MediaFilter.VIDEO) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.VideoFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("${stringResource(R.string.filter_video)} ($videoCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.testTag("filter_video")
        )
        FilterChip(
            selected = selected == MediaFilter.AUDIO,
            onClick = { onSelect(MediaFilter.AUDIO) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("${stringResource(R.string.filter_audio)} ($audioCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.testTag("filter_audio")
        )
    }
}

@Composable
private fun FolderListContent(
    folders: List<MediaFolder>,
    mediaFilter: MediaFilter,
    searchQuery: String,
    onOpenFolder: (String) -> Unit,
    onRemoveFolder: (MediaFolder) -> Unit
) {
    val q = searchQuery.trim()
    val filteredFolders = folders.filter { folder ->
        val matchesFilter = when (mediaFilter) {
            MediaFilter.ALL -> folder.items.isNotEmpty()
            MediaFilter.VIDEO -> folder.videoCount > 0
            MediaFilter.AUDIO -> folder.audioCount > 0
        }
        val matchesQuery = q.isEmpty() ||
            folder.name.contains(q, ignoreCase = true) ||
            folder.items.any { it.title.contains(q, ignoreCase = true) }
        matchesFilter && matchesQuery
    }

    if (filteredFolders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_matching_files),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("folder_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = filteredFolders,
            key = { _, folder -> folder.name }
        ) { index, folder ->
            FolderCard(
                folder = folder,
                index = index,
                onClick = { onOpenFolder(folder.name) },
                onRemove = { onRemoveFolder(folder) }
            )
        }
    }
}

@Composable
private fun FolderCard(
    folder: MediaFolder,
    index: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("folder_card_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.folder_summary,
                        folder.videoCount,
                        folder.audioCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("remove_folder_$index")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.remove_item_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderItemsContent(
    items: List<MediaItemModel>,
    mediaFilter: MediaFilter,
    searchQuery: String,
    onPlayItem: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onRemoveItem: (MediaItemModel) -> Unit
) {
    val q = searchQuery.trim()
    val filtered = items.filter { item ->
        val matchesFilter = when (mediaFilter) {
            MediaFilter.ALL -> true
            MediaFilter.VIDEO -> item.mediaType == MediaType.VIDEO
            MediaFilter.AUDIO -> item.mediaType == MediaType.AUDIO
        }
        val matchesQuery = q.isEmpty() || item.title.contains(q, ignoreCase = true)
        matchesFilter && matchesQuery
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_matching_files),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("media_item_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = filtered,
            key = { _, item -> item.id }
        ) { index, item ->
            MediaItemCard(
                item = item,
                index = index,
                onClick = { onPlayItem(item, filtered) },
                onRemove = { onRemoveItem(item) }
            )
        }
    }
}

@Composable
private fun MediaItemCard(
    item: MediaItemModel,
    index: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val isAudio = item.mediaType == MediaType.AUDIO
    val progress = if (item.durationMs > 0L && item.lastPositionMs > 0L) {
        (item.lastPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("media_item_$index")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isAudio) {
                                    listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.play_video_desc),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = formatDuration(item.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        val sizeText = formatFileSize(item.sizeBytes)
                        if (sizeText.isNotEmpty()) {
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("remove_media_$index")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.remove_item_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (progress > 0.02f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onPickGalleryVideos: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_cinema),
                contentDescription = stringResource(R.string.empty_illustration_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.empty_videos_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage ?: stringResource(R.string.empty_videos_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onPickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pick_folder_button")
        ) {
            Icon(
                imageVector = Icons.Filled.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.add_folder_button))
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onPickFiles,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("pick_files_button")
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.add_files_button))
        }

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(
            onClick = onPickGalleryVideos,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("pick_gallery_button")
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.pick_gallery_videos))
        }
    }
}
