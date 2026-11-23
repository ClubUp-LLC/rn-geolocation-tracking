package us.clubup.geolocation;


import java.util.Map;


/** The optional stop time the app passes inside the headers map. Its presence turns on crew mode. */
final class TrackingUntilKey {

    static final String NAME = "trackingUntil";

    private TrackingUntilKey() {
    }

    static boolean isPresent(Map<String, Object> headers) {

        return headers != null && headers.containsKey(NAME);
    }

    /** Removes the key, so it never goes out as an HTTP header, and returns its time or null. */
    static Long take(Map<String, Object> headers) {

        if (!isPresent(headers)) {
            return null;
        }
        Object value = headers.remove(NAME);
        return value instanceof String ? IsoTime.parse((String) value) : null;
    }
}
