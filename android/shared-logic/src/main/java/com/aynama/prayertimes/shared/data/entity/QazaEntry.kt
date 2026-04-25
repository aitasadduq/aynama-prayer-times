package com.aynama.prayertimes.shared.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

enum class QazaStatus { MISSED, MADE_UP, INTENTION_TO_MAKEUP }

@Entity(
    tableName = "qaza_entries",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "prayer", "date"], unique = true)],
)
data class QazaEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prayer: Prayer,
    val date: LocalDate,
    val status: QazaStatus,
    val profileId: Long,
    val updatedAt: Long,
)
