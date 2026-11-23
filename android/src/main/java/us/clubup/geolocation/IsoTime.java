package us.clubup.geolocation;


import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// java.time needs API 26 and SimpleDateFormat reads "Z" only from API 24, so the library parses by hand.
final class IsoTime {

    private static final Pattern ISO_8601 = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|([+-])(\\d{2}):?(\\d{2}))");

    private IsoTime() {
    }

    /** Returns epoch milliseconds, or null when the text is not an ISO 8601 time with a zone. */
    static Long parse(String text) {

        if (text == null) {
            return null;
        }
        Matcher match = ISO_8601.matcher(text.trim());
        if (!match.matches()) {
            return null;
        }
        try {
            return utcMillis(match) - offsetMillis(match);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long utcMillis(Matcher match) {

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setLenient(false);
        calendar.clear();
        calendar.set(number(match, 1), number(match, 2) - 1, number(match, 3),
                number(match, 4), number(match, 5), number(match, 6));
        calendar.set(Calendar.MILLISECOND, milliseconds(match.group(7)));
        return calendar.getTimeInMillis();
    }

    private static long offsetMillis(Matcher match) {

        if ("Z".equals(match.group(8))) {
            return 0;
        }
        int sign = "-".equals(match.group(9)) ? -1 : 1;
        return sign * (number(match, 10) * 60L + number(match, 11)) * 60_000L;
    }

    private static int milliseconds(String fraction) {

        if (fraction == null) {
            return 0;
        }
        String firstThreeDigits = (fraction + "00").substring(0, 3);
        return Integer.parseInt(firstThreeDigits);
    }

    private static int number(Matcher match, int group) {

        return Integer.parseInt(match.group(group));
    }
}
