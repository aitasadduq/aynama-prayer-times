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
