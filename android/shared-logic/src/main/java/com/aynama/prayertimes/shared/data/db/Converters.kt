package com.aynama.prayertimes.shared.data.db

import androidx.room.TypeConverter
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import java.time.LocalDate

class Converters {
    @TypeConverter fun localDateToLong(value: LocalDate): Long = value.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter fun calculationMethodToString(value: CalculationMethodKey): String = value.name
    @TypeConverter fun stringToCalculationMethod(value: String): CalculationMethodKey =
        CalculationMethodKey.valueOf(value)

    @TypeConverter fun asrMadhabToString(value: AsrMadhab): String = value.name
    @TypeConverter fun stringToAsrMadhab(value: String): AsrMadhab = AsrMadhab.valueOf(value)

    @TypeConverter fun prayerToString(value: Prayer): String = value.name
    @TypeConverter fun stringToPrayer(value: String): Prayer = Prayer.valueOf(value)

    @TypeConverter fun qazaStatusToString(value: QazaStatus): String = value.name
    @TypeConverter fun stringToQazaStatus(value: String): QazaStatus = QazaStatus.valueOf(value)
}
