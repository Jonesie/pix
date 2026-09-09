package nz.net.jonesie.pix.ui.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.net.jonesie.pix.data.repo.PixRepository
import java.io.File

data class UploadUiState(
    val fileName: String? = null,
    val mimeType: String? = null,
    val isVideo: Boolean = false,
    val previewUri: Uri? = null,
    val caption: String = "",
    val description: String = "",
    val tags: String = "",
    val createdDate: String = "",
    val sequence: String = "",
    val uploading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val success: Boolean = false,
)

class UploadViewModel(private val repository: PixRepository) : ViewModel() {

    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state

    private var pickedFile: File? = null

    fun setSelection(context: Context, uri: Uri) {
        viewModelScope.launch {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val isVideo = mimeType.startsWith("video/")
            val name = queryDisplayName(context, uri) ?: "upload_${System.currentTimeMillis()}"

            _state.update {
                it.copy(fileName = name, mimeType = mimeType, isVideo = isVideo, previewUri = uri, error = null)
            }

            val cacheFile = withContext(Dispatchers.IO) {
                val dest = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
                resolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest
            }
            pickedFile = cacheFile
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    fun setCaption(v: String) = _state.update { it.copy(caption = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setTags(v: String) = _state.update { it.copy(tags = v) }
    fun setCreatedDate(v: String) = _state.update { it.copy(createdDate = v) }
    fun setSequence(v: String) = _state.update { it.copy(sequence = v) }

    fun upload() {
        val file = pickedFile
        val current = _state.value
        if (file == null || current.fileName == null || current.mimeType == null) {
            _state.update { it.copy(error = "Choose a photo or video first.") }
            return
        }
        _state.update { it.copy(uploading = true, progress = 0f, error = null) }
        viewModelScope.launch {
            try {
                repository.uploadImage(
                    file = file,
                    mimeType = current.mimeType,
                    originalFilename = current.fileName,
                    caption = current.caption,
                    description = current.description,
                    tags = current.tags,
                    createdDate = current.createdDate,
                    sequence = current.sequence,
                    onProgress = { fraction -> _state.update { it.copy(progress = fraction) } },
                )
                file.delete()
                _state.update { UploadUiState(success = true) }
            } catch (e: Exception) {
                _state.update { it.copy(uploading = false, error = e.message ?: "Upload failed.") }
            }
        }
    }
}
