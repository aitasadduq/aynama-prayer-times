package com.aynama.prayertimes.shared.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aynama.prayertimes.shared.data.dao.ProfileDao
import com.aynama.prayertimes.shared.data.dao.QazaEntryDao
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.QazaEntry

@Database(
    entities = [Profile::class, QazaEntry::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AynamaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun qazaEntryDao(): QazaEntryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN timezone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN use_location_timezone INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun buildInMemory(context: Context): AynamaDatabase =
            Room.inMemoryDatabaseBuilder(context, AynamaDatabase::class.java).build()

        fun build(context: Context): AynamaDatabase =
            Room.databaseBuilder(context, AynamaDatabase::class.java, "aynama.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
