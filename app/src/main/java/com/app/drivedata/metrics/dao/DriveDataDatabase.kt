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
    entities = [MetricRecordEntity::class, UserEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DriveDataDatabase : RoomDatabase() {

    abstract fun metricRecordDao(): MetricRecordDao

    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "drivedata.db"

        /** Adds the users table without dropping existing metric records. */
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

        fun build(context: Context): DriveDataDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DriveDataDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2)
                .build()
    }
}
