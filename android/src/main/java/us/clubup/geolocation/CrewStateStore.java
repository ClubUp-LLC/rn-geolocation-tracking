package us.clubup.geolocation;


import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

import androidx.annotation.Nullable;


/** The newest crew config (token, stop time), so a service the system restarts does not use the first one. */
final class CrewStateStore {

    private static final String PREFERENCES = "us.clubup.geolocation.crew";
    private static final String KEY_UPLOAD_URL = "upload_url";
    private static final String KEY_FAILURE_URL = "failure_url";
    private static final String KEY_HEADERS = "headers";
    private static final String KEY_TRACKING_UNTIL = "tracking_until";

    private final SharedPreferences preferences;

    CrewStateStore(Context context) {

        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    void save(GeolocationConfig config) {

        HashMap<String, Object> headers = config.getHeaders();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_UPLOAD_URL, config.getUploadUrl())
                .putString(KEY_FAILURE_URL, config.getFailureUrl())
                .putString(KEY_HEADERS, new JSONObject(headers != null ? headers : new HashMap<String, Object>()).toString());
        Long trackingUntilMs = config.getTrackingUntilMs();
        if (trackingUntilMs != null) {
            editor.putLong(KEY_TRACKING_UNTIL, trackingUntilMs);
        }
        else {
            editor.remove(KEY_TRACKING_UNTIL);
        }
        editor.commit();
    }

    void saveStopTime(long trackingUntilMs) {

        preferences.edit().putLong(KEY_TRACKING_UNTIL, trackingUntilMs).commit();
    }

    /** The saved crew config, or null when nothing is saved or it cannot be read. */
    @Nullable
    GeolocationConfig load() {

        if (!preferences.contains(KEY_TRACKING_UNTIL)) {
            return null;
        }
        try {
            return GeolocationConfig.crewMode(
                    preferences.getString(KEY_UPLOAD_URL, null),
                    preferences.getString(KEY_FAILURE_URL, null),
                    headersFromJson(preferences.getString(KEY_HEADERS, "{}")),
                    preferences.getLong(KEY_TRACKING_UNTIL, 0));
        }
        catch (JSONException e) {
            return null;
        }
    }

    void clear() {

        preferences.edit().clear().commit();
    }

    private static HashMap<String, Object> headersFromJson(String json) throws JSONException {

        JSONObject object = new JSONObject(json);
        HashMap<String, Object> headers = new HashMap<>();
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            String name = names.next();
            headers.put(name, object.getString(name));
        }
        return headers;
    }
}
