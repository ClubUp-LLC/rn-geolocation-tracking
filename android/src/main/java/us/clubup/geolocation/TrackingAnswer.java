package us.clubup.geolocation;


import org.json.JSONException;
import org.json.JSONObject;


/** What a crew upload answer says: track until a time, stop now, or nothing readable. */
final class TrackingAnswer {

    static final TrackingAnswer STOP = new TrackingAnswer(Kind.STOP, 0);
    static final TrackingAnswer UNREADABLE = new TrackingAnswer(Kind.UNREADABLE, 0);

    private enum Kind { TRACK_UNTIL, STOP, UNREADABLE }

    private final Kind kind;
    private final long stopTimeMs;

    private TrackingAnswer(Kind kind, long stopTimeMs) {

        this.kind = kind;
        this.stopTimeMs = stopTimeMs;
    }

    static TrackingAnswer parse(String body) {

        if (body == null) {
            return UNREADABLE;
        }
        try {
            JSONObject json = new JSONObject(body);
            return of(json.opt("tracking_enabled"), json.opt("tracking_until"));
        }
        catch (JSONException e) {
            return UNREADABLE;
        }
    }

    private static TrackingAnswer of(Object trackingEnabled, Object trackingUntil) {

        if (!(trackingEnabled instanceof Boolean)) {
            return UNREADABLE;
        }
        if (!(Boolean) trackingEnabled || trackingUntil == null || JSONObject.NULL.equals(trackingUntil)) {
            return STOP;
        }
        Long stopTimeMs = trackingUntil instanceof String ? IsoTime.parse((String) trackingUntil) : null;
        return stopTimeMs == null ? UNREADABLE : new TrackingAnswer(Kind.TRACK_UNTIL, stopTimeMs);
    }

    boolean isStop() {

        return kind == Kind.STOP;
    }

    boolean hasStopTime() {

        return kind == Kind.TRACK_UNTIL;
    }

    long stopTimeMs() {

        return stopTimeMs;
    }
}
