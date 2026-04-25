package com.aynama.prayertimes.shared.data.repository

import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.dao.ProfileDao
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {

    fun observeAll(): Flow<List<Profile>> = dao.observeAll()

    suspend fun insert(profile: Profile): Long = dao.insert(profile)

    suspend fun update(profile: Profile) = dao.update(profile)

    suspend fun delete(profile: Profile) = dao.delete(profile)

    suspend fun setGpsProfile(
        name: String,
        latitude: Double,
        longitude: Double,
        method: CalculationMethodKey,
        asrMadhab: AsrMadhab,
        sortOrder: Int = 0,
    ): Long = dao.upsertAsGps(
        Profile(
            name = name,
            latitude = latitude,
            longitude = longitude,
            calculationMethod = method,
            asrMadhab = asrMadhab,
            isGps = true,
            sortOrder = sortOrder,
        )
    )
}
