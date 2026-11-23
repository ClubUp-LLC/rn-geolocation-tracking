package us.clubup.geolocation;


import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


class SendLocations implements Runnable {

    @SuppressWarnings("unused")
    private static final String TAG = "SendLocation";

    private static final int LEGACY_CONNECT_TIMEOUT_MS = 15000;
    private static final int LEGACY_READ_TIMEOUT_MS = 10000;
    private static final int CREW_CONNECT_TIMEOUT_MS = 15000;
    private static final int CREW_READ_TIMEOUT_MS = 15000;

    interface Listener {

        void onAnswered(int statusCode, @Nullable String body);

        void onFailed();
    }

    private final String uploadUrl;
    private final HashMap<String, Object> headers;
    private final List<TrackedPoint> points;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    @Nullable
    private final Listener listener;

    private SendLocations(List<TrackedPoint> points, @NonNull String uploadUrl, @Nullable HashMap<String, Object> headers,
            int connectTimeoutMs, int readTimeoutMs, @Nullable Listener listener) {

        this.uploadUrl = uploadUrl;
        this.headers = headers;
        this.points = points;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.listener = listener;
    }

    /** The 1.1.2 upload: one request per reading, the answer is not read. */
    static SendLocations legacy(List<TrackedPoint> points, @NonNull String uploadUrl, @Nullable HashMap<String, Object> headers) {

        return new SendLocations(points, uploadUrl, headers, LEGACY_CONNECT_TIMEOUT_MS, LEGACY_READ_TIMEOUT_MS, null);
    }

    static SendLocations crew(List<TrackedPoint> points, @NonNull String uploadUrl, @Nullable HashMap<String, Object> headers,
            @NonNull Listener listener) {

        return new SendLocations(points, uploadUrl, headers, CREW_CONNECT_TIMEOUT_MS, CREW_READ_TIMEOUT_MS, listener);
    }

    @Override
    public void run() {

        int statusCode;
        String body = null;
        try {
            HttpURLConnection httpConnection = setupConnection();
            httpConnection.getOutputStream().write(generateBody().getBytes("utf-8"));
            httpConnection.connect();
            statusCode = httpConnection.getResponseCode();
            Log.d(TAG, "Response code: " + statusCode);
            if (listener != null && UploadOutcome.ofStatusCode(statusCode) == UploadOutcome.ACCEPTED) {
                body = readBody(httpConnection);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to send location", e);
            if (listener != null) {
                listener.onFailed();
            }
            return;
        }
        if (listener != null) {
            listener.onAnswered(statusCode, body);
        }
    }

    private HttpURLConnection setupConnection() throws IOException {

        URL url = new URL(uploadUrl);
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();

        httpConnection.setReadTimeout(readTimeoutMs);
        httpConnection.setConnectTimeout(connectTimeoutMs);
        httpConnection.setRequestMethod("POST");
        httpConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpConnection.setRequestProperty("Accept", "application/json");
        addHeaders(httpConnection);
        httpConnection.setDoOutput(true);

        return httpConnection;
    }

    private void addHeaders(HttpURLConnection httpConnection) {

        if (headers != null) {

            for (String headerName : headers.keySet()) {

                Object value = headers.get(headerName);
                if (value != null) {
                    httpConnection.setRequestProperty(headerName, value.toString());
                }
            }
        }
    }

    private static String readBody(HttpURLConnection httpConnection) throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream(), "utf-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private String generateBody() {

        JSONObject root = new JSONObject();

        try {
            JSONArray locationsArray = new JSONArray();

            for (TrackedPoint point : points) {

                JSONObject object = new JSONObject();
                Date time = new Date(point.timeMs);
                DateFormat dateFormat = getDateFormat();

                object.put("time", dateFormat.format(time));
                object.put("latitude", point.latitude);
                object.put("longitude", point.longitude);
                object.put("accuracy", point.accuracy);
                object.put("altitude", point.altitude);
                locationsArray.put(object);
            }

            root.put("points", locationsArray);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        return root.toString();
    }

    private DateFormat getDateFormat() {

        @SuppressLint("SimpleDateFormat")
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }
}
