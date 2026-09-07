package com.aynama.prayertimes.shared.sync

import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile

/**
 * How a profile set travels from the phone to the watch.
 *
 * One codec, used by both sides, for the same reason the countdown rule is stated once: a
 * watch that decodes a profile differently from how the phone encoded it computes different
 * prayer times, and the disagreement would surface as "the watch is wrong" rather than as a
 * parsing bug.
 *
 * Hand-rolled rather than JSON because it has to be unit-testable on a plain JVM. `org.json`
 * is a stub in Android unit tests, and pulling a serialization plugin in for twelve flat
 * fields would cost more than it saves.
 *
 * The format is line-per-profile, unit-separator between fields, with a version line first.
 * A payload whose version is not understood decodes to nothing rather than to a wrong guess —
 * the watch then shows its "waiting for the phone" state instead of stale or garbled times.
 */
object ProfileCodec {

    const val VERSION = "aynama-profiles-v1"

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(profiles: List<Profile>): String = buildString {
        append(VERSION)
        for (p in profiles) {
            append(RECORD)
            append(
                listOf(
                    p.id.toString(),
                    escape(p.name),
                    p.latitude.toString(),
                    p.longitude.toString(),
                    p.calculationMethod.name,
                    p.asrMadhab.name,
                    p.isGps.toString(),
                    p.sortOrder.toString(),
                    escape(p.timezone),
                    p.useLocationTimezone.toString(),
                    p.hijriOffset.toString(),
                    p.hijriOffsetMonthKey.toString(),
                ).joinToString(FIELD.toString()),
            )
        }
    }

    /**
     * Decode a payload, or return null if it is not one of ours or is damaged.
     *
     * Null and empty are different answers: null means "I could not read this", which the
     * watch must not treat as "the phone has no profiles" — that would wipe a working watch
     * because one message arrived corrupt.
     */
    fun decode(payload: String?): List<Profile>? {
        if (payload == null) return null
        val parts = payload.split(RECORD)
        if (parts.firstOrNull() != VERSION) return null
        return parts.drop(1).map { record ->
            val f = record.split(FIELD)
            if (f.size != FIELD_COUNT) return null
            Profile(
                id = f[0].toLongOrNull() ?: return null,
                name = unescape(f[1]),
                latitude = f[2].toDoubleOrNull() ?: return null,
                longitude = f[3].toDoubleOrNull() ?: return null,
                calculationMethod = CalculationMethodKey.entries.firstOrNull { it.name == f[4] }
                    ?: return null,
                asrMadhab = AsrMadhab.entries.firstOrNull { it.name == f[5] } ?: return null,
                isGps = f[6].toBooleanStrictOrNull() ?: return null,
                sortOrder = f[7].toIntOrNull() ?: return null,
                timezone = unescape(f[8]),
                useLocationTimezone = f[9].toBooleanStrictOrNull() ?: return null,
                hijriOffset = f[10].toIntOrNull() ?: return null,
                hijriOffsetMonthKey = f[11].toIntOrNull() ?: return null,
            )
        }
    }

    // Profile names are user text. Nothing stops someone naming a profile with the separator
    // characters themselves, and an unescaped one would shift every following field by one.
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(FIELD.toString(), "\\u001F")
        .replace(RECORD.toString(), "\\u001E")

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i == value.lastIndex) {
                out.append(c)
                i++
                continue
            }
            when {
                value.startsWith("\\\\", i) -> { out.append('\\'); i += 2 }
                value.startsWith("\\u001F", i) -> { out.append(FIELD); i += 6 }
                value.startsWith("\\u001E", i) -> { out.append(RECORD); i += 6 }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private const val FIELD_COUNT = 12
}
