package nz.net.jonesie.pix.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shared, app-wide "are we logged in as admin" flag so every screen reacts to login/logout. */
class SessionManager(private val repository: PixRepository) {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    suspend fun refresh() {
        _isAuthenticated.value = try {
            repository.isAuthenticated()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun login(password: String) {
        repository.login(password)
        _isAuthenticated.value = true
    }

    suspend fun logout() {
        try {
            repository.logout()
        } finally {
            _isAuthenticated.value = false
        }
    }
}
