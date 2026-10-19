package com.app.drivedata.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.drivedata.DriveDataApplication
import com.app.drivedata.auth.AuthSession
import com.app.drivedata.auth.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Error shown by the login screen. */
enum class LoginError {
    INVALID_CREDENTIALS,
    UNEXPECTED,
}

/** State rendered by the login screen. */
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val error: LoginError? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.isNotEmpty()
}

class LoginViewModel(
    private val usersRepository: UserRepository,
    private val authSession: AuthSession,
) : ViewModel() {

    private val username = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val error = MutableStateFlow<LoginError?>(null)
    private val isSubmitting = MutableStateFlow(false)

    val uiState: StateFlow<LoginUiState> =
        combine(username, password, error, isSubmitting) { usernameValue, passwordValue, currentError, submitting ->
            LoginUiState(
                username = usernameValue,
                password = passwordValue,
                error = currentError,
                isSubmitting = submitting,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LoginUiState(),
        )

    fun onUsernameChange(value: String) {
        username.value = value
        error.value = null
    }

    fun onPasswordChange(value: String) {
        password.value = value
        error.value = null
    }

    fun login() {
        if (isSubmitting.value) return

        val enteredUsername = username.value.trim()
        val enteredPassword = password.value
        if (enteredUsername.isEmpty() || enteredPassword.isEmpty()) return

        isSubmitting.value = true
        error.value = null
        viewModelScope.launch {
            try {
                if (usersRepository.authenticate(enteredUsername, enteredPassword)) {
                    password.value = ""
                    authSession.signIn(enteredUsername)
                } else {
                    error.value = LoginError.INVALID_CREDENTIALS
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error.value = LoginError.UNEXPECTED
            } finally {
                isSubmitting.value = false
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(application: DriveDataApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    LoginViewModel(
                        usersRepository = application.container.usersRepository,
                        authSession = application.container.authSession,
                    )
                }
            }
    }
}
