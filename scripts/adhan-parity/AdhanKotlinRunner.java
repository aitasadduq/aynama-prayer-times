import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

/**
 * Emits one case's prayer times as JSON, computed by the same Adhan-Kotlin release the Android
 * app ships (com.batoulapps.adhan:adhan:1.2.1, pinned in scripts/reference-versions.json).
 *
 * Deliberately a thin mirror of AdhanWrapper.kt rather than a second opinion: the vectors this
 * feeds are the contract iOS is held to, so they must be what Android actually produces —
 * including the same Shafii/Hanafi double call. Times are emitted as HH:MM, the resolution the
 * vectors are compared at, in Locale.ROOT: a JVM defaulting to a locale with its own numerals
 * would otherwise write vectors the Swift parser rejects.
 *
 * Usage: java -cp .:adhan-1.2.1.jar AdhanKotlinRunner <lat> <lng> <yyyy-mm-dd> <tz> <METHOD>
 */
public final class AdhanKotlinRunner {

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println(
                "usage: AdhanKotlinRunner <lat> <lng> <yyyy-mm-dd> <timezone> <METHOD>");
            System.exit(2);
        }
        double latitude = Double.parseDouble(args[0]);
        double longitude = Double.parseDouble(args[1]);
        LocalDate date = LocalDate.parse(args[2]);
        ZoneId zone = ZoneId.of(args[3]);
        CalculationMethod method = mapMethod(args[4]);

        Coordinates coordinates = new Coordinates(latitude, longitude);
        DateComponents components =
            new DateComponents(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

        CalculationParameters shafiiParams = method.getParameters();
        shafiiParams.madhab = Madhab.SHAFI;
        PrayerTimes shafii = new PrayerTimes(coordinates, components, shafiiParams);

        CalculationParameters hanafiParams = method.getParameters();
        hanafiParams.madhab = Madhab.HANAFI;
        PrayerTimes hanafi = new PrayerTimes(coordinates, components, hanafiParams);

        if (shafii.fajr == null || shafii.sunrise == null || shafii.dhuhr == null
            || shafii.asr == null || shafii.maghrib == null || shafii.isha == null
            || hanafi.asr == null) {
            System.out.println("{\"unavailable\": true}");
            return;
        }

        System.out.println(
            "{"
                + field("fajr", shafii.fajr, zone) + ", "
                + field("sunrise", shafii.sunrise, zone) + ", "
                + field("dhuhr", shafii.dhuhr, zone) + ", "
                + field("asr_shafii", shafii.asr, zone) + ", "
                + field("asr_hanafi", hanafi.asr, zone) + ", "
                + field("maghrib", shafii.maghrib, zone) + ", "
                + field("isha", shafii.isha, zone)
                + ", \"high_latitude_rule\": \"" + shafiiParams.highLatitudeRule + "\""
                + "}");
    }

    private static String field(String name, Date value, ZoneId zone) {
        LocalTime time =
            value.toInstant().atZone(zone).toLocalTime().truncatedTo(ChronoUnit.SECONDS);
        return "\"" + name + "\": \""
            + String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute())
            + "\"";
    }

    private static CalculationMethod mapMethod(String key) {
        switch (key) {
            case "MWL": return CalculationMethod.MUSLIM_WORLD_LEAGUE;
            case "ISNA": return CalculationMethod.NORTH_AMERICA;
            case "UMM_AL_QURA": return CalculationMethod.UMM_AL_QURA;
            case "EGYPTIAN": return CalculationMethod.EGYPTIAN;
            case "KARACHI": return CalculationMethod.KARACHI;
            case "DUBAI": return CalculationMethod.DUBAI;
            case "MOON_SIGHTING_COMMITTEE": return CalculationMethod.MOON_SIGHTING_COMMITTEE;
            case "KUWAIT": return CalculationMethod.KUWAIT;
            case "QATAR": return CalculationMethod.QATAR;
            case "SINGAPORE": return CalculationMethod.SINGAPORE;
            default: throw new IllegalArgumentException("unknown method: " + key);
        }
    }
}
