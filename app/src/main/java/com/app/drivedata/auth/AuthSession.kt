package com.app.drivedata.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthSession {

    private val _signedInUser = MutableStateFlow<String?>(null)

    val signedInUser: StateFlow<String?> = _signedInUser.asStateFlow()

    fun signIn(username: String) {
        _signedInUser.value = username
    }

    fun signOut() {
        _signedInUser.value = null
    }
}
