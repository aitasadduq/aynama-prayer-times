package com.aynama.prayertimes.shared.data.repository

import com.aynama.prayertimes.shared.data.dao.QazaEntryDao
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.QazaEntry
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class QazaRepository(private val dao: QazaEntryDao) {

    fun observeByDateRange(profileId: Long, from: LocalDate, to: LocalDate): Flow<List<QazaEntry>> =
        dao.observeByDateRange(profileId, from.toEpochDay(), to.toEpochDay())

    fun observeOutstandingCount(profileId: Long): Flow<Int> =
        dao.observeOutstandingCount(profileId)

    suspend fun markPrayer(profileId: Long, prayer: Prayer, date: LocalDate, status: QazaStatus) {
        dao.upsert(
            QazaEntry(
                prayer = prayer,
                date = date,
                status = status,
                profileId = profileId,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun autoMarkMissed(profileId: Long, prayer: Prayer, date: LocalDate) {
        dao.insertIfAbsent(
            QazaEntry(
                prayer = prayer,
                date = date,
                status = QazaStatus.MISSED,
                profileId = profileId,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
