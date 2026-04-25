package com.aynama.prayertimes.shared.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aynama.prayertimes.shared.data.dao.ProfileDao
import com.aynama.prayertimes.shared.data.dao.QazaEntryDao
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.QazaEntry

@Database(
    entities = [Profile::class, QazaEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AynamaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun qazaEntryDao(): QazaEntryDao

    companion object {
        fun buildInMemory(context: Context): AynamaDatabase =
            Room.inMemoryDatabaseBuilder(context, AynamaDatabase::class.java).build()

        fun build(context: Context): AynamaDatabase =
            Room.databaseBuilder(context, AynamaDatabase::class.java, "aynama.db").build()
    }
}
