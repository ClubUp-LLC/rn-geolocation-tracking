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
import android.content.pm.ServiceInfo;
import android.content.res.TypedArray;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final String WAKE_LOCK_TAG = "clubup:geolocationTick";
    private static final long WAKE_LOCK_LIMIT_MS = 30_000L;


    @Nullable
    private static GeolocationService instance = null;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private int startId;

    @Nullable
    private GeolocationConfig config;

    private FusedLocationProviderClient fusedLocationClient;
    private boolean isTracking;

    @Nullable
    private CrewTracker crewTracker;
    private CrewStateStore crewStateStore;
    private Handler mainHandler;
    private PowerManager.WakeLock tickWakeLock;


    @Override
    public void onCreate() {

        super.onCreate();
        instance = this;
        createNotificationChannel(this);
        crewStateStore = new CrewStateStore(this);
        mainHandler = new Handler(Looper.getMainLooper());
        tickWakeLock = newWakeLock();
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
        releaseTickWakeLock();
        instance = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "onStartCommand " + intent);

        if (intent != null) {

            String action = intent.getAction();
            this.startId = startId;

            switch (action) {
                case ACTION_START:
                    handleStart(intent, (flags & START_FLAG_REDELIVERY) != 0);
                    return START_REDELIVER_INTENT;
                case ACTION_STOP:
                    handleStop(crewTracker != null ? crewTracker.stop() : Collections.<TrackedPoint>emptyList());
                    return START_NOT_STICKY;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void handleStart(Intent intent, boolean redelivered) {

        startAsForeground();
        GeolocationConfig startConfig = intent.getParcelableExtra(EXTRA_CONFIG);
        if (startConfig.isCrewMode()) {
            startCrewMode(startConfig, redelivered);
        }
        else {
            endCrewMode();
            config = startConfig;
            startTracking();
        }
    }

    private void startCrewMode(GeolocationConfig startConfig, boolean redelivered) {

        // A redelivered intent holds the first token and stop time; the store holds the newest.
        GeolocationConfig crewConfig = redelivered ? crewStateStore.load() : startConfig;
        Long stopTimeMs = crewConfig != null ? crewConfig.getTrackingUntilMs() : null;
        long nowMs = System.currentTimeMillis();
        boolean mayTrack = redelivered ? CrewTracker.canResume(stopTimeMs, nowMs) : stopTimeMs != null;
        if (!mayTrack) {
            handleStop(crewTracker != null ? crewTracker.stop() : Collections.<TrackedPoint>emptyList());
            return;
        }

        crewStateStore.save(crewConfig);
        config = crewConfig;
        if (crewTracker == null) {
            crewTracker = new CrewTracker(stopTimeMs, nowMs);
        }
        else {
            crewTracker.changeStopTime(stopTimeMs);
        }
        startTracking();
        runCrewTick();
    }

    private void endCrewMode() {

        crewTracker = null;
        CrewAlarms.cancelAll(this);
        crewStateStore.clear();
        releaseTickWakeLock();
    }

    /** Stops everything; lastPoints are crew points sent once, without retry, with their answer ignored. */
    private void handleStop(List<TrackedPoint> lastPoints) {

        Log.d(TAG, "stopTracking");

        if (!lastPoints.isEmpty() && config != null) {
            sendLastPoints(lastPoints, config);
        }
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        endCrewMode();
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID_TRACKING, notification);
        } else {
            startForeground(NOTIFICATION_ID_TRACKING, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        }
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

        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        }
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

        if (crewTracker != null) {
            for (Location location : locations) {
                crewTracker.addReading(toTrackedPoint(location));
            }
            runCrewTick();
        }
        else if (config != null) {
            executorService.execute(SendLocations.legacy(toTrackedPoints(locations), config.getUploadUrl(), config.getHeaders()));
        }
    }

    private void runCrewTick() {

        if (crewTracker == null) {
            return;
        }
        tickWakeLock.acquire(WAKE_LOCK_LIMIT_MS);
        long nowMs = System.currentTimeMillis();
        CrewTracker.Tick tick = crewTracker.tick(nowMs);
        if (tick.action == CrewTracker.Tick.Action.STOP) {
            handleStop(tick.batch);
            return;
        }
        if (tick.action == CrewTracker.Tick.Action.SEND) {
            uploadCrewBatch(tick);
        }
        CrewAlarms.scheduleHeartbeat(this, crewTracker.heartbeatAlarmAt(nowMs));
        CrewAlarms.scheduleStopTime(this, crewTracker.stopAlarmAt(nowMs));
        if (!crewTracker.isUploading()) {
            releaseTickWakeLock();
        }
    }

    private void uploadCrewBatch(CrewTracker.Tick tick) {

        final CrewTracker tracker = crewTracker;
        final int uploadId = tick.uploadId;
        executorService.execute(SendLocations.crew(tick.batch, config.getUploadUrl(), config.getHeaders(),
                new SendLocations.Listener() {

                    @Override
                    public void onAnswered(int statusCode, @Nullable String body) {

                        mainHandler.post(() -> onCrewUploadAnswered(tracker, uploadId, statusCode, body));
                    }

                    @Override
                    public void onFailed() {

                        mainHandler.post(() -> onCrewUploadFailed(tracker, uploadId));
                    }
                }));
    }

    private void onCrewUploadAnswered(CrewTracker tracker, int uploadId, int statusCode, @Nullable String body) {

        if (tracker != crewTracker) {
            return;
        }
        long previousStopTimeMs = tracker.stopTimeMs();
        tracker.onUploadAnswered(uploadId, statusCode, body);
        if (tracker.stopTimeMs() != previousStopTimeMs) {
            crewStateStore.saveStopTime(tracker.stopTimeMs());
        }
        runCrewTick();
    }

    private void onCrewUploadFailed(CrewTracker tracker, int uploadId) {

        if (tracker != crewTracker) {
            return;
        }
        tracker.onUploadFailed(uploadId);
        runCrewTick();
    }

    private void sendLastPoints(List<TrackedPoint> points, GeolocationConfig lastConfig) {

        final PowerManager.WakeLock sendWakeLock = newWakeLock();
        sendWakeLock.acquire(WAKE_LOCK_LIMIT_MS);
        executorService.execute(SendLocations.crew(points, lastConfig.getUploadUrl(), lastConfig.getHeaders(),
                new SendLocations.Listener() {

                    @Override
                    public void onAnswered(int statusCode, @Nullable String body) {

                        release(sendWakeLock);
                    }

                    @Override
                    public void onFailed() {

                        release(sendWakeLock);
                    }
                }));
    }

    private PowerManager.WakeLock newWakeLock() {

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        wakeLock.setReferenceCounted(false);
        return wakeLock;
    }

    private void releaseTickWakeLock() {

        if (tickWakeLock != null) {
            release(tickWakeLock);
        }
    }

    private static void release(PowerManager.WakeLock wakeLock) {

        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private static List<TrackedPoint> toTrackedPoints(List<Location> locations) {

        List<TrackedPoint> points = new ArrayList<>(locations.size());
        for (Location location : locations) {
            points.add(toTrackedPoint(location));
        }
        return points;
    }

    private static TrackedPoint toTrackedPoint(Location location) {

        return new TrackedPoint(location.getTime(), location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), location.getAltitude());
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

    static void runCrewTickIfTracking() {

        if (instance != null) {
            instance.runCrewTick();
        }
    }

    public static void startTracking(Context context, GeolocationConfig config) {

        // A crew start, or a 1.1.2 start that ends crew mode, must reach the running service.
        boolean alreadyTracking = instance != null && instance.isTracking && instance.crewTracker == null;
        if (alreadyTracking && !config.isCrewMode()) {
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

    /** Works without in-memory state: the stop also reaches a service the system restarted for an earlier app run. */
    public static void stopTracking(Context context) {

        new CrewStateStore(context).clear();
        CrewAlarms.cancelAll(context);

        Intent intent = new Intent(context, GeolocationService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        }
        catch (IllegalStateException | SecurityException e) {
            Log.e(TAG, "Unable to stop service", e);
        }
    }
}
