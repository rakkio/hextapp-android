package com.hextapp.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.data.repository.AuthRepository
import com.hextapp.android.data.repository.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val session: UserSession? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false,
    val showChangePassword: Boolean = false,
    val updateSuccess: String? = null,
)

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val isSubmitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val isEditing = MutableStateFlow(false)
    private val showChangePassword = MutableStateFlow(false)
    private val updateSuccess = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.session,
        isSubmitting,
        error,
        isEditing,
        showChangePassword,
    ) { session, submitting, err, editing, changePw ->
        ProfileUiState(
            session = session,
            isSubmitting = submitting,
            error = err,
            isEditing = editing,
            showChangePassword = changePw,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    // updateSuccess is not part of the combine (6-param combine not available), so expose separately
    // and merge via a secondary state; simpler: add it to combine by chaining
    // We'll use a 5-flow combine and separately track updateSuccess in a parallel flow
    // Actually Kotlin's combine supports up to 5 flows in extension form; for 6 we need nested combine.
    // The uiState above doesn't include updateSuccess due to the 5-flow limit.
    // We expose updateSuccess separately and merge it in the actual observed state via a wrapper.
    val updateSuccessState: StateFlow<String?> = updateSuccess

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) { error.value = "Introduce tu email y contraseña"; return }
        viewModelScope.launch {
            isSubmitting.value = true; error.value = null
            authRepository.login(email.trim(), password).onFailure { error.value = it.message ?: "Error al iniciar sesión" }
            isSubmitting.value = false
        }
    }

    fun register(name: String, username: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.length < 6) { error.value = "Completa nombre, email y contraseña (mínimo 6 caracteres)"; return }
        viewModelScope.launch {
            isSubmitting.value = true; error.value = null
            authRepository.register(name.trim(), username.trim(), email.trim(), password).onFailure { error.value = it.message ?: "Error al registrarse" }
            isSubmitting.value = false
        }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }
    fun clearError() { error.value = null }
    fun toggleEditing() { isEditing.value = !isEditing.value; error.value = null }
    fun toggleChangePassword() { showChangePassword.value = !showChangePassword.value; error.value = null }
    fun clearSuccess() { updateSuccess.value = null }

    fun updateProfile(name: String, bio: String, location: String) {
        if (name.isBlank()) { error.value = "El nombre no puede estar vacío"; return }
        viewModelScope.launch {
            isSubmitting.value = true; error.value = null
            authRepository.updateProfile(name.trim(), bio.trim(), location.trim())
                .onSuccess { isEditing.value = false; updateSuccess.value = "Perfil actualizado" }
                .onFailure { error.value = it.message ?: "Error al actualizar perfil" }
            isSubmitting.value = false
        }
    }

    fun changePassword(currentPw: String, newPw: String, confirmPw: String) {
        if (currentPw.isBlank() || newPw.length < 6) { error.value = "La nueva contraseña debe tener al menos 6 caracteres"; return }
        if (newPw != confirmPw) { error.value = "Las contraseñas no coinciden"; return }
        viewModelScope.launch {
            isSubmitting.value = true; error.value = null
            authRepository.changePassword(currentPw, newPw)
                .onSuccess { showChangePassword.value = false; updateSuccess.value = "Contraseña cambiada correctamente" }
                .onFailure { error.value = it.message ?: "Error al cambiar contraseña" }
            isSubmitting.value = false
        }
    }

    companion object {
        fun factory(authRepo: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(authRepo) as T
        }
    }
}
