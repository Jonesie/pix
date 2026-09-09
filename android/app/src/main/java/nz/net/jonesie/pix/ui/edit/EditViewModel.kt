package nz.net.jonesie.pix.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.net.jonesie.pix.data.repo.PixRepository

data class EditUiState(
    val loading: Boolean = true,
    val isVideo: Boolean = false,
    val thumbFilename: String? = null,
    val caption: String = "",
    val description: String = "",
    val tags: String = "",
    val createdDate: String = "",
    val sequence: String = "",
    val rotating: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class EditViewModel(
    private val repository: PixRepository,
    private val imageId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state

    init {
        viewModelScope.launch {
            try {
                val image = repository.getImage(imageId)
                _state.update {
                    it.copy(
                        loading = false,
                        isVideo = image.isVideo,
                        thumbFilename = image.filename_thumb,
                        caption = image.caption,
                        description = image.description,
                        tags = image.tags.joinToString(", "),
                        createdDate = image.created_date ?: "",
                        sequence = image.sequence?.toString() ?: "",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load this image.") }
            }
        }
    }

    fun setCaption(v: String) = _state.update { it.copy(caption = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setTags(v: String) = _state.update { it.copy(tags = v) }
    fun setCreatedDate(v: String) = _state.update { it.copy(createdDate = v) }
    fun setSequence(v: String) = _state.update { it.copy(sequence = v) }

    fun rotate(degrees: Int) {
        if (_state.value.isVideo || _state.value.rotating) return
        _state.update { it.copy(rotating = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = repository.rotateImage(imageId, degrees)
                _state.update { it.copy(rotating = false, thumbFilename = updated.filename_thumb) }
            } catch (e: Exception) {
                _state.update { it.copy(rotating = false, error = e.message ?: "Rotate failed.") }
            }
        }
    }

    fun save() {
        val current = _state.value
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.updateImage(
                    imageId,
                    caption = current.caption,
                    description = current.description,
                    tags = current.tags,
                    createdDate = current.createdDate.takeIf { it.isNotBlank() },
                    sequence = current.sequence.takeIf { it.isNotBlank() },
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Save failed.") }
            }
        }
    }
}
