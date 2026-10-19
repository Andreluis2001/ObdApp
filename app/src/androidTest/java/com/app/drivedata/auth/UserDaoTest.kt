package com.app.drivedata.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.drivedata.auth.dao.UserDao
import com.app.drivedata.auth.dao.UserEntity
import com.app.drivedata.metrics.dao.DriveDataDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var database: DriveDataDatabase
    private lateinit var dao: UserDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, DriveDataDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.userDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findByUsername_returnsTheStoredUser() =
        runBlocking {
            val user = user("admin", passwordHash = "hash")
            dao.insert(user)

            assertEquals(user, dao.findByUsername("admin"))
            assertNull(dao.findByUsername("missing"))
        }

    @Test
    fun insert_keepsTheFirstUserForADuplicateUsername() =
        runBlocking {
            dao.insert(user("admin", passwordHash = "first"))
            dao.insert(user("admin", passwordHash = "second"))

            assertEquals(1, dao.count())
            assertEquals("first", dao.findByUsername("admin")?.passwordHash)
        }

    private fun user(username: String, passwordHash: String): UserEntity =
        UserEntity(
            username = username,
            passwordHash = passwordHash,
            passwordSalt = "salt",
            createdAtMillis = 1_700_000_000_000L,
        )
}
