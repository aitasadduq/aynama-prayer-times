package com.aynama.prayertimes.shared.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aynama.prayertimes.shared.data.entity.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<Profile>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("UPDATE profiles SET isGps = 0")
    suspend fun clearGpsFlag()

    /**
     * Make the stored profiles match [profiles] exactly, ids included.
     *
     * The watch mirrors the phone rather than owning profiles, and the ids have to survive
     * the crossing: a complication or tile that remembers "profile 3" must still mean the
     * same place after a sync.
     *
     * Updates in place rather than re-inserting. Neither `DELETE FROM profiles` nor
     * `@Insert(REPLACE)` is safe here: SQLite implements `INSERT OR REPLACE` as a delete
     * followed by an insert, so both cascade through the Qaḍā foreign key and take the
     * tracker history of a profile that is still perfectly current. Rows that are genuinely
     * gone from the phone are deleted deliberately; the rest are updated.
     */
    @Transaction
    suspend fun mirror(profiles: List<Profile>) {
        val existing = getAllOnce().associateBy { it.id }
        val incoming = profiles.mapTo(HashSet()) { it.id }
        existing.values.filterNot { it.id in incoming }.forEach { delete(it) }
        for (profile in profiles) {
            // insert keeps an explicit non-zero id; autoGenerate only assigns one at 0.
            if (existing.containsKey(profile.id)) update(profile) else insert(profile)
        }
    }

    @Transaction
    suspend fun upsertAsGps(profile: Profile): Long {
        clearGpsFlag()
        return if (profile.id == 0L) {
            insert(profile.copy(isGps = true))
        } else {
            update(profile.copy(isGps = true))
            profile.id
        }
    }
}
