package com.app.drivedata.auth

import com.app.drivedata.auth.dao.UserDao
import com.app.drivedata.auth.dao.UserEntity

class UserRepository(
    private val dao: UserDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun ensureDefaultUser() {
        if (dao.count() > 0) return

        val salt = PasswordHasher.newSalt()
        dao.insert(
            UserEntity(
                username = DEFAULT_USERNAME,
                passwordHash = PasswordHasher.hash(DEFAULT_PASSWORD, salt),
                passwordSalt = salt,
                createdAtMillis = clock(),
            ),
        )
    }

    suspend fun authenticate(username: String, password: String): Boolean {
        ensureDefaultUser()

        val user = dao.findByUsername(username.trim()) ?: return false
        return PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)
    }

    companion object {
        const val DEFAULT_USERNAME = "admin"
        const val DEFAULT_PASSWORD = "admin"
    }
}
