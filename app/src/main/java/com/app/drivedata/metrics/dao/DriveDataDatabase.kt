package com.app.drivedata.metrics.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.drivedata.auth.dao.UserDao
import com.app.drivedata.auth.dao.UserEntity
import com.app.drivedata.vehicle.dao.VehicleDao
import com.app.drivedata.vehicle.entity.Elm327ConnectionEntity
import com.app.drivedata.vehicle.entity.SupportedPidEntity
import com.app.drivedata.vehicle.entity.VehicleEntity

@Database(
    entities = [
        MetricRecordEntity::class,
        MetricReadingEntity::class,
        UserEntity::class,
        VehicleEntity::class,
        Elm327ConnectionEntity::class,
        SupportedPidEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class DriveDataDatabase : RoomDatabase() {

    abstract fun metricRecordDao(): MetricRecordDao

    abstract fun userDao(): UserDao

    abstract fun vehicleDao(): VehicleDao

    companion object {
        const val DATABASE_NAME = "drivedata.db"

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `users` (" +
                            "`username` TEXT NOT NULL, " +
                            "`password_hash` TEXT NOT NULL, " +
                            "`password_salt` TEXT NOT NULL, " +
                            "`created_at_millis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`username`))",
                    )
                }
            }

        /**
         * Replaces the rpm-only record table with the snapshot + readings model.
         *
         * Pre-release telemetry has no value, so old rows are intentionally discarded instead of
         * backfilled. Users and credentials are preserved.
         */
        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `metric_records`")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `metric_records` (" +
                            "`id` TEXT NOT NULL, " +
                            "`owner_username` TEXT, " +
                            "`vin` TEXT, " +
                            "`collected_at_millis` INTEGER NOT NULL, " +
                            "`upload_status` TEXT NOT NULL, " +
                            "`uploaded_at_millis` INTEGER, " +
                            "`last_error` TEXT, " +
                            "`attempt_count` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_metric_records_owner_status_collected` " +
                            "ON `metric_records` (`owner_username`, `upload_status`, `collected_at_millis`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `metric_readings` (" +
                            "`record_id` TEXT NOT NULL, " +
                            "`metric_id` TEXT NOT NULL, " +
                            "`unit` TEXT NOT NULL, " +
                            "`value` REAL, " +
                            "`is_available` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`record_id`, `metric_id`), " +
                            "FOREIGN KEY(`record_id`) REFERENCES `metric_records`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_metric_readings_record_id` " +
                            "ON `metric_readings` (`record_id`)",
                    )
                }
            }

        /**
         * Adds the vehicle domain: vehicles, their ELM327 connection settings and discovered PIDs.
         *
         * Existing telemetry and user rows are untouched.
         */
        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `vehicles` (" +
                            "`id` TEXT NOT NULL, " +
                            "`owner_username` TEXT NOT NULL, " +
                            "`nickname` TEXT, " +
                            "`make` TEXT NOT NULL, " +
                            "`model` TEXT NOT NULL, " +
                            "`year` INTEGER, " +
                            "`license_plate` TEXT, " +
                            "`is_active` INTEGER NOT NULL DEFAULT 0, " +
                            "`created_at_millis` INTEGER NOT NULL, " +
                            "`updated_at_millis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_vehicles_owner_active` " +
                            "ON `vehicles` (`owner_username`, `is_active`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `elm327_connections` (" +
                            "`vehicle_id` TEXT NOT NULL, " +
                            "`host` TEXT NOT NULL, " +
                            "`port` INTEGER NOT NULL, " +
                            "`last_connection_at_millis` INTEGER, " +
                            "`last_ecu_communication_at_millis` INTEGER, " +
                            "`connection_status` TEXT NOT NULL, " +
                            "PRIMARY KEY(`vehicle_id`), " +
                            "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `supported_pids` (" +
                            "`vehicle_id` TEXT NOT NULL, " +
                            "`pid` INTEGER NOT NULL, " +
                            "`discovered_at_millis` INTEGER NOT NULL, " +
                            "`enabled_for_collection` INTEGER NOT NULL DEFAULT 1, " +
                            "PRIMARY KEY(`vehicle_id`, `pid`), " +
                            "FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_supported_pids_vehicle_id` " +
                            "ON `supported_pids` (`vehicle_id`)",
                    )
                }
            }

        /**
         * Drops the nickname and year vehicle columns. SQLite on the minimum supported SDK cannot
         * drop columns, so the vehicles table is recreated and its rows copied over. Foreign key
         * enforcement is deferred for the duration of the migration transaction, allowing the
         * parent table to be dropped while the connection/PID child tables still reference it.
         */
        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA defer_foreign_keys = TRUE")
                    db.execSQL(
                        "CREATE TABLE `vehicles_new` (" +
                            "`id` TEXT NOT NULL, " +
                            "`owner_username` TEXT NOT NULL, " +
                            "`make` TEXT NOT NULL, " +
                            "`model` TEXT NOT NULL, " +
                            "`license_plate` TEXT, " +
                            "`is_active` INTEGER NOT NULL DEFAULT 0, " +
                            "`created_at_millis` INTEGER NOT NULL, " +
                            "`updated_at_millis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "INSERT INTO `vehicles_new` " +
                            "(`id`, `owner_username`, `make`, `model`, `license_plate`, " +
                            "`is_active`, `created_at_millis`, `updated_at_millis`) " +
                            "SELECT `id`, `owner_username`, `make`, `model`, `license_plate`, " +
                            "`is_active`, `created_at_millis`, `updated_at_millis` FROM `vehicles`",
                    )
                    db.execSQL("DROP TABLE `vehicles`")
                    db.execSQL("ALTER TABLE `vehicles_new` RENAME TO `vehicles`")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_vehicles_owner_active` " +
                            "ON `vehicles` (`owner_username`, `is_active`)",
                    )
                }
            }

        fun build(context: Context): DriveDataDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DriveDataDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
