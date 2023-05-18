package us.clubup.geolocation;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;


public class GeolocationService extends Service {

    private static final String TAG = "GeolocationService";

    private static final String ACTION_START = "start";
    private static final String ACTION_STOP = "stop";

    private static final String EXTRA_CONFIG = "config";

    private static final int NOTIFICATION_ID_TRACKING = 3862;
    private static final String NOTIFICATION_CHANNEL_ID = "location_service_channel";


    @Nullable
    private static GeolocationService instance = null;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private int startId;

    @Nullable
    private GeolocationConfig config;

    private FusedLocationProviderClient fusedLocationClient;
    private boolean isTracking;


    @Override
    public void onCreate() {

        super.onCreate();
        instance = this;
        createNotificationChannel(this);
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
        instance = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "onStartCommand " + intent);

        if (intent != null) {

            String action = intent.getAction();

            switch (action) {
                case ACTION_START:
                    handleStart(intent, startId);
                    return START_REDELIVER_INTENT;
                case ACTION_STOP:
                    handleStop();
                    return START_NOT_STICKY;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void handleStart(Intent intent, int startId) {

        this.startId = startId;
        startAsForeground();
        config = intent.getParcelableExtra(EXTRA_CONFIG);
        startTracking();
    }

    private void handleStop() {

        Log.d(TAG, "stopTracking");

        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        config = null;
        isTracking = false;
        stopForeground(true);
        stopSelf(startId);
    }

    private void startAsForeground() {

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(getNotificationSmallIcon())
                .setContentTitle(getString(R.string.geolocation_notification_title))
                .setContentText(getString(R.string.geolocation_notification_message))
                .setColor(getNotificationColor())
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID_TRACKING, notification);
    }

    @ColorInt
    private int getNotificationColor() {

        TypedValue typedValue = new TypedValue();

        TypedArray a = getApplicationContext().obtainStyledAttributes(typedValue.data,
                new int[] { R.attr.notificationAccentColor, R.attr.colorAccent });
        int color = a.getColor(0, 0);
        if (color == 0) {
            color = a.getColor(1, 0);
        }
        a.recycle();

        return (color != 0)
               ? color
               : ResourcesCompat.getColor(getResources(), R.color.notification_accent, null);
    }

    @DrawableRes
    private int getNotificationSmallIcon() {

        TypedValue typedValue = new TypedValue();
        TypedArray a = getApplicationContext().obtainStyledAttributes(typedValue.data,
                new int[] { R.attr.notificationIcon });
        int drawableId = a.getResourceId(0, R.drawable.ic_notification);
        a.recycle();

        return drawableId;
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {

        Log.d(TAG, "startTracking");

        isTracking = true;

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        if (hasLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(config.toLocationRequest(), locationCallback, Looper.getMainLooper());
        }
    }

    private boolean hasLocationPermission() {

        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
               || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void processLocations(List<Location> locations) {

        if (config != null) {
            executorService.execute(new SendLocations(locations, config.getUploadUrl(), config.getHeaders()));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    private final LocationCallback locationCallback = new LocationCallback() {

        @Override
        public void onLocationResult(LocationResult locationResult) {

            super.onLocationResult(locationResult);

            if (locationResult.getLocations().size() > 0) {
                processLocations(locationResult.getLocations());
            }
        }
    };

    private static void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notification_channel_name_location_service),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void startTracking(Context context, GeolocationConfig config) {

        boolean alreadyTracking = instance != null && instance.isTracking;
        if (alreadyTracking) {
            Log.w(TAG, "Already tracking.");
            return;
        }

        Intent intent = new Intent(context, GeolocationService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_CONFIG, config);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startForegroundService(intent);
            }
            catch (IllegalStateException | SecurityException e) {
                // couldn't start a foreground service when the app is in background
                Log.e(TAG, "Unable to start service", e);
            }
        }
        else {
            context.startService(intent);
        }
    }

    public static void stopTracking(Context context) {

        boolean alreadyStopped = instance == null || !instance.isTracking;
        if (alreadyStopped) {
            Log.w(TAG, "Already not tracking.");
            return;
        }

        Intent intent = new Intent(context, GeolocationService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
