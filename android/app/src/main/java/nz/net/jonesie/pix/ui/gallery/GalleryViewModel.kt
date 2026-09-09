package nz.net.jonesie.pix.ui.gallery

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
import nz.net.jonesie.pix.data.model.Tag
import nz.net.jonesie.pix.data.repo.PixRepository
import nz.net.jonesie.pix.data.repo.SessionManager

data class GalleryUiState(
    val items: List<PixImage> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val activeTag: String = "",
    val query: String = "",
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
)

class GalleryViewModel(
    private val repository: PixRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = combine(_state, session.isAuthenticated) { s, auth ->
        s.copy(isAuthenticated = auth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryUiState())

    init {
        loadTags()
        loadMore(reset = true)
        viewModelScope.launch { session.refresh() }
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                val tags = repository.listTags()
                _state.update { it.copy(tags = tags) }
            } catch (e: Exception) {
                // Tag chips are a nice-to-have; a failure here shouldn't block the gallery.
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        loadMore(reset = true)
    }

    fun selectTag(tag: String) {
        _state.update { it.copy(activeTag = tag) }
        loadMore(reset = true)
    }

    fun refresh() {
        loadTags()
        loadMore(reset = true)
        viewModelScope.launch { session.refresh() }
    }

    fun loadMore(reset: Boolean = false) {
        val current = _state.value
        if (!reset && (current.loading || !current.hasMore)) return
        val offset = if (reset) 0 else current.offset
        if (reset) {
            _state.update { it.copy(items = emptyList(), offset = 0, hasMore = true, loading = true, error = null) }
        } else {
            _state.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            try {
                val response = repository.listImages(current.query, current.activeTag, offset)
                _state.update {
                    it.copy(
                        items = if (reset) response.items else it.items + response.items,
                        offset = response.next_offset,
                        hasMore = response.has_more,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load the gallery.") }
            }
        }
    }

    fun deleteImage(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteImage(id)
                _state.update { it.copy(items = it.items.filterNot { img -> img.id == id }) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Delete failed.") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { session.logout() }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
