package nz.net.jonesie.pix.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.net.jonesie.pix.data.repo.SessionManager

data class LoginUiState(
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun setPassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun login() {
        val password = _state.value.password
        if (password.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                session.login(password)
                _state.update { it.copy(loading = false, loggedIn = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Wrong password.") }
            }
        }
    }
}
