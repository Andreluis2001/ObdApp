package com.app.drivedata.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory sign-in state; it does not survive a process restart. */
class AuthSession {

    private val _signedInUser = MutableStateFlow<String?>(null)

    /** Username of the signed-in user, or null while signed out. */
    val signedInUser: StateFlow<String?> = _signedInUser.asStateFlow()

    fun signIn(username: String) {
        _signedInUser.value = username
    }

    fun signOut() {
        _signedInUser.value = null
    }
}
