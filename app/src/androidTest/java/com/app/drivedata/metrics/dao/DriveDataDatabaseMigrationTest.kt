package com.app.drivedata.metrics.dao

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveDataDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DriveDataDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate2To3_discardsLegacyTelemetryButKeepsUsers() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO `users` (`username`, `password_hash`, `password_salt`, `created_at_millis`) " +
                    "VALUES ('admin', 'hash', 'salt', 1)",
            )
            execSQL(
                "INSERT INTO `metric_records` (`id`, `vin`, `rpm`, `collected_at_millis`, `upload_status`) " +
                    "VALUES ('legacy', 'VIN', 900.0, 1, 'PENDING')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 3, true, DriveDataDatabase.MIGRATION_2_3).apply {
            query("SELECT COUNT(*) FROM `metric_records`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM `users`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM `metric_readings`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate3To4_addsVehicleTablesAndKeepsExistingRows() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                "INSERT INTO `users` (`username`, `password_hash`, `password_salt`, `created_at_millis`) " +
                    "VALUES ('admin', 'hash', 'salt', 1)",
            )
            execSQL(
                "INSERT INTO `metric_records` (`id`, `owner_username`, `vin`, `collected_at_millis`, `upload_status`) " +
                    "VALUES ('record', 'admin', 'VIN', 1, 'PENDING')",
            )
            execSQL(
                "INSERT INTO `metric_readings` (`record_id`, `metric_id`, `unit`, `value`, `is_available`) " +
                    "VALUES ('record', 'RPM', 'rpm', 900.0, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, DriveDataDatabase.MIGRATION_3_4)
            .apply {
                query("SELECT COUNT(*) FROM `vehicles`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
                query("SELECT COUNT(*) FROM `elm327_connections`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
                query("SELECT COUNT(*) FROM `supported_pids`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
                query("SELECT COUNT(*) FROM `users`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                query("SELECT COUNT(*) FROM `metric_records`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                query("SELECT COUNT(*) FROM `metric_readings`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                close()
            }
    }

    @Test
    fun migrate5To6_addsIdentityColumnsAndKeepsExistingVehicles() {
        helper.createDatabase(TEST_DATABASE, 5).apply {
            execSQL(
                "INSERT INTO `vehicles` (`id`, `owner_username`, `make`, `model`, " +
                    "`license_plate`, `is_active`, `created_at_millis`, `updated_at_millis`) " +
                    "VALUES ('v1', 'admin', 'Chevrolet', 'S10', 'ABC1234', 1, 1, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 6, true, DriveDataDatabase.MIGRATION_5_6)
            .apply {
                query(
                    "SELECT `make`, `vin`, `manufacturer`, `serial_number` " +
                        "FROM `vehicles` WHERE `id` = 'v1'",
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("Chevrolet", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                    assertTrue(cursor.isNull(2))
                    assertTrue(cursor.isNull(3))
                }
                close()
            }
    }

    @Test
    fun migrate6To7_dropsLocalUsersAndKeepsTelemetry() {
        helper.createDatabase(TEST_DATABASE, 6).apply {
            execSQL(
                "INSERT INTO `users` (`username`, `password_hash`, `password_salt`, `created_at_millis`) " +
                    "VALUES ('admin', 'hash', 'salt', 1)",
            )
            execSQL(
                "INSERT INTO `metric_records` (`id`, `owner_username`, `vin`, `collected_at_millis`, " +
                    "`upload_status`) VALUES ('record', 'admin', 'VIN', 1, 'PENDING')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 7, true, DriveDataDatabase.MIGRATION_6_7)
            .apply {
                query("SELECT COUNT(*) FROM `metric_records`").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                close()
            }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}