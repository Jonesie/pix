package nz.net.jonesie.pix.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
fun EditScreen(imageId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val viewModel: EditViewModel = viewModel(factory = viewModelFactory {
        initializer { EditViewModel(container.repository, imageId) }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit image") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.thumbFilename?.let { thumb ->
                AsyncImage(
                    model = MediaUrls.thumb(context, thumb),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }

            if (!state.isVideo) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.rotate(-90) }, enabled = !state.rotating) {
                        Icon(Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Rotate left")
                    }
                    OutlinedButton(onClick = { viewModel.rotate(90) }, enabled = !state.rotating) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Rotate right")
                    }
                }
            }

            OutlinedTextField(
                value = state.caption,
                onValueChange = viewModel::setCaption,
                label = { Text("Caption") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tags,
                onValueChange = viewModel::setTags,
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.createdDate,
                onValueChange = viewModel::setCreatedDate,
                label = { Text("Created date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.sequence,
                onValueChange = viewModel::setSequence,
                label = { Text("Sequence number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}
