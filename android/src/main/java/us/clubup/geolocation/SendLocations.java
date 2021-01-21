package us.clubup.geolocation;


import android.annotation.SuppressLint;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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

    private final String uploadUrl;
    private final HashMap<String, Object> headers;
    private final List<Location> locations;

    public SendLocations(List<Location> locations, @NonNull String uploadUrl, @Nullable HashMap<String, Object> headers) {

        this.uploadUrl = uploadUrl;
        this.headers = headers;
        this.locations = locations;
    }

    @Override
    public void run() {

        try {
            HttpURLConnection httpConnection = setupConnection();
            httpConnection.getOutputStream().write(generateBody().getBytes("utf-8"));
            httpConnection.connect();
            Log.d(TAG, "Response code: " + httpConnection.getResponseCode());

        } catch (Exception e) {
            Log.e(TAG, "Failed to send location", e);
        }
    }

    private HttpURLConnection setupConnection() throws IOException {

        URL url = new URL(uploadUrl);
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();

        httpConnection.setReadTimeout(10000);
        httpConnection.setConnectTimeout(15000);
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

    private String generateBody() {

        JSONObject root = new JSONObject();

        try {
            JSONArray locationsArray = new JSONArray();

            for (Location location : locations) {

                JSONObject object = new JSONObject();
                Date time = new Date(location.getTime());
                DateFormat dateFormat = getDateFormat();

                object.put("time", dateFormat.format(time));
                object.put("latitude", location.getLatitude());
                object.put("longitude", location.getLongitude());
                object.put("accuracy", location.getAccuracy());
                object.put("altitude", location.getAltitude());
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
