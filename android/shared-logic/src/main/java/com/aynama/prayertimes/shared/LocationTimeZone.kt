package com.aynama.prayertimes.shared

import com.aynama.prayertimes.shared.data.entity.Profile
import java.io.DataInputStream
import java.time.ZoneId
import java.util.Locale

/**
 * The IANA time zone of a point, given the country it is in (DESIGN.md §17, DS32).
 *
 * Offline: the country's zone boundaries are a quadtree bundled as `timezone-lookup.bin`,
 * built from timezone-boundary-builder by `scripts/timezone-lookup/generate.py`, which
 * documents the format.
 */
object LocationTimeZone {

    private const val INNER: Byte = -1

    private class Country(val zones: List<String>, val tree: ByteArray)

    // A missing or unreadable table leaves every zone blank, so profiles fall back to the device zone.
    private val countries: Map<String, Country> by lazy { runCatching { load() }.getOrDefault(emptyMap()) }

    /** The zone at the point, or "" when the country is unknown or this device lacks the zone. */
    fun detect(countryCode: String?, latitude: Double, longitude: Double): String {
        val country = countries[countryCode?.uppercase(Locale.ROOT)] ?: return ""
        val zone = country.zones[country.leaf(latitude, longitude)]
        return if (zone in ZoneId.getAvailableZoneIds()) zone else ""
    }

    private fun Country.leaf(latitude: Double, longitude: Double): Int {
        var x = -180.0
        var y = -180.0
        var size = 360.0
        var pos = 0
        while (tree[pos] == INNER) {
            size /= 2
            val quadrant = (if (latitude >= y + size) 2 else 0) + (if (longitude >= x + size) 1 else 0)
            if (quadrant and 1 != 0) x += size
            if (quadrant and 2 != 0) y += size
            pos++
            repeat(quadrant) { pos = tree.skipSubtree(pos) }
        }
        return tree[pos].toInt() and 0xFF
    }

    private fun ByteArray.skipSubtree(start: Int): Int {
        var pos = start
        var pending = 1
        while (pending > 0) {
            pending += if (this[pos] == INNER) 3 else -1
            pos++
        }
        return pos
    }

    private fun load(): Map<String, Country> {
        val stream = LocationTimeZone::class.java.getResourceAsStream("/timezone-lookup.bin")
            ?: return emptyMap()
        return DataInputStream(stream.buffered()).use { input ->
            fun ascii(length: Int) = String(ByteArray(length).also(input::readFully), Charsets.US_ASCII)
            check(ascii(4) == "TZL1") { "unknown timezone-lookup.bin format" }
            List(input.readUnsignedShort()) {
                val code = ascii(2)
                val zones = List(input.readUnsignedByte()) { ascii(input.readUnsignedByte()) }
                code to Country(zones, ByteArray(input.readInt()).also(input::readFully))
            }.toMap()
        }
    }
}

/**
 * This city profile with its zone detected again, for profiles saved before DS32's fix.
 *
 * The old longitude guess only ever chose among the country's own zones, so the stored zone
 * still names the country; [regionOf] turns it back into a country code. GPS profiles hold the
 * device's zone, not a guess, and are left alone, as is any profile the lookup can't place.
 */
fun Profile.withRedetectedTimezone(regionOf: (zoneId: String) -> String?): Profile {
    if (isGps || timezone.isBlank()) return this
    val zone = LocationTimeZone.detect(regionOf(timezone), latitude, longitude)
    return if (zone.isBlank() || zone == timezone) this else copy(timezone = zone)
}
