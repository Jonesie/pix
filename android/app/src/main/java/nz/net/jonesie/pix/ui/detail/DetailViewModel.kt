package nz.net.jonesie.pix.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.net.jonesie.pix.data.model.PixImage
import nz.net.jonesie.pix.data.repo.PixRepository
import nz.net.jonesie.pix.data.repo.SessionManager

data class DetailUiState(
    val image: PixImage? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val deleted: Boolean = false,
)

class DetailViewModel(
    private val repository: PixRepository,
    session: SessionManager,
    initialId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = combine(_state, session.isAuthenticated) { s, auth ->
        s.copy(isAuthenticated = auth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    init {
        load(initialId)
    }

    fun load(id: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val image = repository.getImage(id)
                _state.update { it.copy(image = image, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load this image.") }
            }
        }
    }

    fun delete() {
        val id = _state.value.image?.id ?: return
        viewModelScope.launch {
            try {
                repository.deleteImage(id)
                _state.update { it.copy(deleted = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Delete failed.") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
