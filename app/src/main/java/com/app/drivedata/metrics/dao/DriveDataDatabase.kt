package com.app.drivedata.metrics.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.drivedata.auth.dao.UserDao
import com.app.drivedata.auth.dao.UserEntity

@Database(
    entities = [MetricRecordEntity::class, MetricReadingEntity::class, UserEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class DriveDataDatabase : RoomDatabase() {

    abstract fun metricRecordDao(): MetricRecordDao

    abstract fun userDao(): UserDao

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

        fun build(context: Context): DriveDataDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DriveDataDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
