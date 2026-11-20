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
    val error: String? = null
)

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val isSubmitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.session,
        isSubmitting,
        error
    ) { session, submitting, err ->
        ProfileUiState(session = session, isSubmitting = submitting, error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            error.value = "Introduce tu email y contraseña"
            return
        }
        viewModelScope.launch {
            isSubmitting.value = true
            error.value = null
            authRepository.login(email.trim(), password).onFailure {
                error.value = it.message ?: "Error al iniciar sesión"
            }
            isSubmitting.value = false
        }
    }

    fun register(name: String, username: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.length < 6) {
            error.value = "Completa nombre, email y contraseña (mínimo 6 caracteres)"
            return
        }
        viewModelScope.launch {
            isSubmitting.value = true
            error.value = null
            authRepository.register(name.trim(), username.trim(), email.trim(), password).onFailure {
                error.value = it.message ?: "Error al registrarse"
            }
            isSubmitting.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearError() {
        error.value = null
    }

    companion object {
        fun factory(authRepo: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(authRepo) as T
        }
    }
}
