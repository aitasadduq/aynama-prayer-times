package com.aynama.prayertimes.tracker

import com.aynama.prayertimes.shared.data.entity.Prayer
import com.aynama.prayertimes.shared.data.entity.QazaStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class TrackerViewModelTest {

    private val monday = LocalDate.of(2026, 5, 4)  // known Monday

    @Test
    fun `weekLabel — this week returns 'This week'`() {
        assertEquals("This week", weekLabel(monday, monday))
    }

    @Test
    fun `weekLabel — last week returns 'Last week'`() {
        val lastMonday = monday.minusWeeks(1)
        assertEquals("Last week", weekLabel(lastMonday, monday))
    }

    @Test
    fun `weekLabel — two weeks ago returns date range`() {
        val twoWeeksAgo = monday.minusWeeks(2)
        val result = weekLabel(twoWeeksAgo, monday)
        assertEquals("Apr 20–Apr 26", result)
    }

    @Test
    fun `weekLabel — three weeks ago returns date range`() {
        val threeWeeksAgo = monday.minusWeeks(3)
        val result = weekLabel(threeWeeksAgo, monday)
        assertEquals("Apr 13–Apr 19", result)
    }

    @Test
    fun `prayedCount — null status does not count`() {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { null }
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(0, count)
    }

    @Test
    fun `prayedCount — PRAYED_ON_TIME counts`() {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { QazaStatus.PRAYED_ON_TIME }
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(5, count)
    }

    @Test
    fun `prayedCount — MISSED does not count`() {
        val prayers: Map<Prayer, QazaStatus?> = mapOf(
            Prayer.FAJR to QazaStatus.MISSED,
            Prayer.DHUHR to QazaStatus.PRAYED_ON_TIME,
            Prayer.ASR to QazaStatus.PRAYED_ON_TIME,
            Prayer.MAGHRIB to QazaStatus.PRAYED_ON_TIME,
            Prayer.ISHA to QazaStatus.PRAYED_ON_TIME,
        )
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(4, count)
    }

    @Test
    fun `prayedCount — INTENTION_TO_MAKEUP does not count`() {
        val prayers: Map<Prayer, QazaStatus?> = mapOf(
            Prayer.FAJR to QazaStatus.INTENTION_TO_MAKEUP,
            Prayer.DHUHR to QazaStatus.INTENTION_TO_MAKEUP,
            Prayer.ASR to null,
            Prayer.MAGHRIB to null,
            Prayer.ISHA to QazaStatus.MADE_UP,
        )
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(1, count)
    }

    @Test
    fun `prayedCount — MADE_UP counts`() {
        val prayers: Map<Prayer, QazaStatus?> = Prayer.entries.associateWith { QazaStatus.MADE_UP }
        val count = prayers.values.count { it == QazaStatus.PRAYED_ON_TIME || it == QazaStatus.MADE_UP }
        assertEquals(5, count)
    }

    @Test
    fun `today is anchored to a Monday`() {
        val date = LocalDate.of(2026, 5, 9)  // Saturday
        val thisWeekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 5, 4), thisWeekMonday)
    }
}
