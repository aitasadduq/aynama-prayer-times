package com.aynama.prayertimes.shared

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.shared.data.db.AynamaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileLocationMigrationTest {
    @Test
    fun addingLocationNamesPreservesExistingProfilesAndPrayerHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "location-migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open(
            "com.aynama.prayertimes.shared.data.db.AynamaDatabase/4.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                old.execSQL("INSERT INTO profiles VALUES (1, 'Home', 21.3871, 39.8688, 'UMM_AL_QURA', 'SHAFII', 0, 0, 'Asia/Riyadh', 1, 0, 0)")
                old.execSQL("INSERT INTO qaza_entries VALUES (1, 'FAJR', 20000, 'MISSED', 1, 1234)")
                old.version = 4
            }
            val db = Room.databaseBuilder(context, AynamaDatabase::class.java, name)
                .addMigrations(AynamaDatabase.MIGRATION_4_5).build()
            try {
                val profile = db.profileDao().observeAll().first().single()
                assertEquals("Home", profile.name)
                assertEquals(21.3871, profile.latitude, 0.0)
                assertEquals("Asia/Riyadh", profile.timezone)
                assertNull(profile.locationName)
                db.profileDao().update(profile.copy(locationName = "Makkah, Saudi Arabia"))
                assertEquals("Makkah, Saudi Arabia", db.profileDao().observeAll().first().single().locationName)
                db.openHelper.readableDatabase.query("SELECT status FROM qaza_entries WHERE profileId = 1").use {
                    check(it.moveToFirst())
                    assertEquals("MISSED", it.getString(0))
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
