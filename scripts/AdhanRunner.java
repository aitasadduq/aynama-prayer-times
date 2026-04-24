import com.batoulapps.adhan.*;
import com.batoulapps.adhan.data.*;
import java.text.*;
import java.util.*;

/**
 * Subprocess wrapper around Adhan 1.2.1 used by generate_vectors.py.
 * Args: lat lon year month day timezone method
 * Stdout: JSON with fajr/sunrise/dhuhr/asr_shafii/asr_hanafi/maghrib/isha (HH:mm local time).
 * Exit 1 on unknown method or other error.
 *
 * Supported methods (Adhan 1.2.1 CalculationMethod enum):
 *   MWL, ISNA, UMM_AL_QURA, EGYPTIAN, KARACHI,
 *   DUBAI, KUWAIT, QATAR, SINGAPORE, MOON_SIGHTING_COMMITTEE
 *
 * Not in Adhan 1.2.1: TEHRAN, GULF, FRANCE, TURKEY — rejected with exit 2.
 * Compile: javac -cp adhan-1.2.1.jar AdhanRunner.java
 * Run:     java -cp .:adhan-1.2.1.jar AdhanRunner 21.4225 39.8262 2026 3 21 Asia/Riyadh MWL
 */
public class AdhanRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: AdhanRunner lat lon year month day timezone method");
            System.exit(1);
        }
        double lat = Double.parseDouble(args[0]);
        double lon = Double.parseDouble(args[1]);
        int year = Integer.parseInt(args[2]);
        int month = Integer.parseInt(args[3]);
        int day = Integer.parseInt(args[4]);
        String tzId = args[5];
        String methodStr = args[6];

        CalculationParameters params = mapMethod(methodStr);
        Coordinates coords = new Coordinates(lat, lon);
        DateComponents date = new DateComponents(year, month, day);
        TimeZone tz = TimeZone.getTimeZone(tzId);

        params.madhab = Madhab.SHAFI;
        PrayerTimes shafii = new PrayerTimes(coords, date, params);

        CalculationParameters paramsH = mapMethod(methodStr);
        paramsH.madhab = Madhab.HANAFI;
        PrayerTimes hanafi = new PrayerTimes(coords, date, paramsH);

        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
        fmt.setTimeZone(tz);

        System.out.printf(
            "{\"fajr\":\"%s\",\"sunrise\":\"%s\",\"dhuhr\":\"%s\","
            + "\"asr_shafii\":\"%s\",\"asr_hanafi\":\"%s\","
            + "\"maghrib\":\"%s\",\"isha\":\"%s\"}%n",
            fmt.format(shafii.fajr),
            fmt.format(shafii.sunrise),
            fmt.format(shafii.dhuhr),
            fmt.format(shafii.asr),
            fmt.format(hanafi.asr),
            fmt.format(shafii.maghrib),
            fmt.format(shafii.isha));
    }

    private static CalculationParameters mapMethod(String method) {
        switch (method) {
            case "MWL":                    return CalculationMethod.MUSLIM_WORLD_LEAGUE.get();
            case "ISNA":                   return CalculationMethod.NORTH_AMERICA.get();
            case "UMM_AL_QURA":            return CalculationMethod.UMM_AL_QURA.get();
            case "EGYPTIAN":               return CalculationMethod.EGYPTIAN.get();
            case "KARACHI":                return CalculationMethod.KARACHI.get();
            case "DUBAI":                  return CalculationMethod.DUBAI.get();
            case "KUWAIT":                 return CalculationMethod.KUWAIT.get();
            case "QATAR":                  return CalculationMethod.QATAR.get();
            case "SINGAPORE":              return CalculationMethod.SINGAPORE.get();
            case "MOON_SIGHTING_COMMITTEE": return CalculationMethod.MOON_SIGHTING_COMMITTEE.get();
            default:
                System.err.println("Method not supported by Adhan 1.2.1: " + method);
                System.exit(2);
                return null;
        }
    }
}
