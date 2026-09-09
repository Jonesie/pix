package nz.net.jonesie.pix.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.net.jonesie.pix.data.api.ServerConfig
import nz.net.jonesie.pix.data.repo.PixRepository

data class ServerUrlUiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class ServerUrlViewModel(
    private val context: Context,
    private val repository: PixRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerUrlUiState(url = ServerConfig.getBaseUrl(context) ?: ""))
    val state: StateFlow<ServerUrlUiState> = _state

    fun setUrl(value: String) {
        _state.update { it.copy(url = value, error = null) }
    }

    fun submit() {
        val normalized = ServerConfig.normalize(_state.value.url)
        if (!ServerConfig.isValid(normalized)) {
            _state.update { it.copy(error = "That doesn't look like a valid URL.") }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            ServerConfig.setBaseUrl(context, normalized)
            try {
                // Cheap, unauthenticated call — just confirms this is actually a pix server.
                repository.listTags()
                _state.update { it.copy(loading = false, saved = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = "Couldn't reach a pix server at that address.")
                }
            }
        }
    }
}
