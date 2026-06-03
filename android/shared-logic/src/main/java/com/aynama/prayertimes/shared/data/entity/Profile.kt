package com.aynama.prayertimes.shared.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aynama.prayertimes.shared.CalculationMethodKey
import java.time.ZoneId

enum class AsrMadhab { SHAFII, HANAFI }

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val calculationMethod: CalculationMethodKey,
    val asrMadhab: AsrMadhab,
    val isGps: Boolean,
    val sortOrder: Int,
    val timezone: String = "",
    val useLocationTimezone: Boolean = false,
    @ColumnInfo(name = "ramadanOffset") val hijriOffset: Int = 0,
    // Adjusted-calendar Hijri month (year*12+month) the offset was set for; offset auto-expires
    // once the perceived month changes. 0 = none.
    val hijriOffsetMonthKey: Int = 0,
)

fun Profile.effectiveZoneId(): ZoneId =
    if (useLocationTimezone && timezone.isNotBlank()) ZoneId.of(timezone)
    else ZoneId.systemDefault()
