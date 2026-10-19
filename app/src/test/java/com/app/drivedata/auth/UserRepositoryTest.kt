package com.app.drivedata.auth

import com.app.drivedata.auth.dao.UserDao
import com.app.drivedata.auth.dao.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTest {

    private val dao = RecordingUserDao()
    private val repository = UserRepository(dao, clock = { 1_700_000_000_000L })

    @Test
    fun ensureDefaultUser_seedsAdminOnlyOnce() =
        runBlocking {
            repository.ensureDefaultUser()
            repository.ensureDefaultUser()

            val user = dao.users.single()
            assertEquals(UserRepository.DEFAULT_USERNAME, user.username)
            assertEquals(1, dao.users.size)
        }

    @Test
    fun ensureDefaultUser_neverStoresThePasswordInClearText() =
        runBlocking {
            repository.ensureDefaultUser()

            val user = dao.users.single()
            assertNotEquals(UserRepository.DEFAULT_PASSWORD, user.passwordHash)
            assertTrue(user.passwordSalt.isNotBlank())
            assertTrue(PasswordHasher.verify(UserRepository.DEFAULT_PASSWORD, user.passwordSalt, user.passwordHash))
        }

    @Test
    fun authenticate_acceptsTheDefaultCredentials() =
        runBlocking {
            assertTrue(repository.authenticate("admin", "admin"))
        }

    @Test
    fun authenticate_rejectsAWrongPassword() =
        runBlocking {
            assertFalse(repository.authenticate("admin", "not-admin"))
        }

    @Test
    fun authenticate_rejectsAnUnknownUsername() =
        runBlocking {
            assertFalse(repository.authenticate("someone-else", "admin"))
        }
}

/** In-memory DAO stand-in so the repository tests run without the Android runtime. */
private class RecordingUserDao : UserDao {
    val users = mutableListOf<UserEntity>()

    override suspend fun findByUsername(username: String): UserEntity? =
        users.firstOrNull { it.username == username }

    override suspend fun insert(user: UserEntity) {
        if (users.none { it.username == user.username }) {
            users += user
        }
    }

    override suspend fun count(): Int = users.size
}
