package nz.net.jonesie.pix.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import nz.net.jonesie.pix.data.api.MediaUrls
import nz.net.jonesie.pix.data.model.PixImage
import nz.net.jonesie.pix.ui.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    onOpenDetail: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenEdit: (String) -> Unit,
    onOpenServerSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: GalleryViewModel = viewModel(factory = viewModelFactory {
        initializer { GalleryViewModel(container.repository, container.session) }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Edit/Upload screens signal a refresh via the back stack's savedStateHandle.
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        val handle = currentEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow("refresh", false).collectLatest { needsRefresh ->
            if (needsRefresh) {
                viewModel.refresh()
                handle["refresh"] = false
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("_pix_") },
                actions = {
                    if (state.isAuthenticated) {
                        IconButton(onClick = onOpenUpload) {
                            Icon(Icons.Default.Add, contentDescription = "Upload")
                        }
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                        }
                    } else {
                        IconButton(onClick = onOpenLogin) {
                            Icon(Icons.Default.Login, contentDescription = "Log in")
                        }
                    }
                    IconButton(onClick = onOpenServerSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Server settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search captions & descriptions…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.activeTag.isEmpty(),
                        onClick = { viewModel.selectTag("") },
                        label = { Text("All") },
                    )
                }
                lazyRowItems(state.tags) { tag ->
                    FilterChip(
                        selected = state.activeTag == tag.name,
                        onClick = { viewModel.selectTag(tag.name) },
                        label = { Text("${tag.name} (${tag.count})") },
                    )
                }
            }

            val gridState = rememberLazyGridState()
            LaunchedEffect(gridState, state.items.size, state.hasMore) {
                lastVisibleIndexFlow(gridState).collectLatest { lastVisible ->
                    if (lastVisible != null && lastVisible >= state.items.size - 6) {
                        viewModel.loadMore()
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isEmpty(),
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.items.isEmpty() && !state.loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No images yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { image ->
                            GalleryCard(
                                image = image,
                                isAuthenticated = state.isAuthenticated,
                                onClick = { onOpenDetail(image.id) },
                                onEdit = { onOpenEdit(image.id) },
                                onDelete = { pendingDeleteId = image.id },
                            )
                        }
                        if (state.loading && state.items.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this image permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteImage(id)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GalleryCard(
    image: PixImage,
    isAuthenticated: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = MediaUrls.thumb(context, image.filename_thumb),
                contentDescription = image.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (image.isVideo) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                )
            }
            if (isAuthenticated) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CardIconButton(Icons.Default.Edit, "Edit", onEdit)
                    CardIconButton(Icons.Default.Delete, "Delete", onDelete)
                }
            }
        }
        if (image.caption.isNotBlank()) {
            Text(
                image.caption,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CardIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

private fun lastVisibleIndexFlow(gridState: LazyGridState) =
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
