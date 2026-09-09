package nz.net.jonesie.pix.ui.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import nz.net.jonesie.pix.data.api.MediaUrls
import nz.net.jonesie.pix.ui.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    imageId: String,
    onBack: () -> Unit,
    onOpenEdit: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: DetailViewModel = viewModel(factory = viewModelFactory {
        initializer { DetailViewModel(container.repository, container.session, imageId) }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(imageId) { viewModel.load(imageId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.image?.caption?.takeIf { it.isNotBlank() } ?: "_pix_") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isAuthenticated) {
                        IconButton(onClick = { onOpenEdit(imageId) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.image != null -> {
                    val image = state.image!!
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                if (image.isVideo) {
                                    VideoPlayer(url = MediaUrls.full(image.filename_full), modifier = Modifier.fillMaxSize())
                                } else {
                                    AsyncImage(
                                        model = MediaUrls.full(image.filename_full),
                                        contentDescription = image.caption,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = { image.prev_id?.let(onNavigateTo) },
                                    enabled = image.prev_id != null,
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Prev")
                                }
                                TextButton(
                                    onClick = { image.next_id?.let(onNavigateTo) },
                                    enabled = image.next_id != null,
                                ) {
                                    Text("Next")
                                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                if (image.caption.isNotBlank()) {
                                    Text(image.caption, style = MaterialTheme.typography.titleMedium)
                                }
                                if (image.description.isNotBlank()) {
                                    Text(
                                        image.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                image.created_date?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                        if (image.tags.isNotEmpty()) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(image.tags) { tag ->
                                        AssistChip(onClick = {}, label = { Text(tag) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this image permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
