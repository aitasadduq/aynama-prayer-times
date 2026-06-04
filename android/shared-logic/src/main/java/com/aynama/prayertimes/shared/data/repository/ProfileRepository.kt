package com.aynama.prayertimes.shared.data.repository

import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.dao.ProfileDao
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(private val dao: ProfileDao) {

    fun observeAll(): Flow<List<Profile>> = dao.observeAll()

    /**
     * The single profile screens default to when they don't let the user pick one
     * (Home pager and Notifications picker excluded). Prefers the GPS profile, otherwise
     * the lowest sortOrder. Shared by Qibla and Tracker so they never disagree.
     */
    fun observeDefaultProfile(): Flow<Profile?> = observeAll().map { profiles ->
        profiles.firstOrNull { it.isGps } ?: profiles.minByOrNull { it.sortOrder }
    }

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
