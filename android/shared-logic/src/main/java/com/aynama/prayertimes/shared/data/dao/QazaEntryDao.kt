package com.aynama.prayertimes.shared.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aynama.prayertimes.shared.data.entity.QazaEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface QazaEntryDao {
    @Query(
        "SELECT * FROM qaza_entries WHERE profileId = :profileId AND date >= :fromEpochDay AND date <= :toEpochDay ORDER BY date ASC"
    )
    fun observeByDateRange(profileId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<QazaEntry>>

    @Query(
        "SELECT COUNT(*) FROM qaza_entries WHERE profileId = :profileId AND status IN ('MISSED', 'INTENTION_TO_MAKEUP')"
    )
    fun observeOutstandingCount(profileId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: QazaEntry): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: QazaEntry): Long
}
