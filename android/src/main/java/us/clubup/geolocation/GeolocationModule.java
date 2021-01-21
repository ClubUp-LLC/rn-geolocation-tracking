package us.clubup.geolocation;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class GeolocationModule extends ReactContextBaseJavaModule implements PermissionListener, ActivityEventListener {

    private GeolocationConfig config;
    private Promise startPromise;

    private static final int REQUEST_CODE_PERMISSIONS = 42;
    private static final int REQUEST_CODE_LOCATION_SETTINGS = 24;

    public GeolocationModule(@Nullable ReactApplicationContext reactContext) {

        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @NonNull
    @Override
    public String getName() {

        return "Geolocation";
    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void startTracking(String uploadUrl, ReadableMap headers, int updatesIntervalSeconds, int distanceFilter, Promise promise) {

        this.config = new GeolocationConfig(uploadUrl, headers.toHashMap(), updatesIntervalSeconds, distanceFilter);
        this.startPromise = promise;

        performChecksAndStart();
    }


    @ReactMethod
    @SuppressWarnings("unused")
    public void stopTracking(Promise promise) {

        GeolocationService.stopTracking(getContext());
        promise.resolve(true);
    }

    private void performChecksAndStart() {

        checkLocationServiceAndStart();
    }

    private void checkLocationServiceAndStart() {

        LocationRequest locationRequest = config.toLocationRequest();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(getContext());
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build())
                                                    .addOnSuccessListener(locationSettingsResponse -> {
                                                        checkPermissionsAndStart();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        if (e instanceof ResolvableApiException) {
                                                            // Location settings are not satisfied, but this can be fixed
                                                            // by showing the user a dialog.
                                                            try {
                                                                // Show the dialog by calling startResolutionForResult(),
                                                                // and check the result in onActivityResult().
                                                                ResolvableApiException resolvable = (ResolvableApiException) e;
                                                                resolvable.startResolutionForResult(getCurrentActivity(), REQUEST_CODE_LOCATION_SETTINGS);
                                                            } catch (IntentSender.SendIntentException sendEx) {
                                                                // Ignore the error.
                                                            }
                                                        }
                                                    });
    }

    private void checkPermissionsAndStart() {

        if (!hasForegroundLocationPermission()) {
            requestForegroundLocationPermission();
        }
        else {
            startTracking();
        }
    }

    private boolean hasForegroundLocationPermission() {

        Context context = getReactApplicationContext();

        boolean hasFineLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                            == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                              == PackageManager.PERMISSION_GRANTED;

        return hasFineLocationPermission || hasCoarseLocationPermission;
    }

    private void requestForegroundLocationPermission() {

        Activity activity = getCurrentActivity();

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {

            new AlertDialog.Builder(activity)
                    .setMessage(R.string.alert_message_location_permission)
                    .setNegativeButton(R.string.no_thanks, ((dialog, which) -> missingPermission()))
                    .setPositiveButton(R.string.settings, (dialog, which) -> goToSettings())
                    .show();
        }
        else {

            String[] permissions = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
            ((PermissionAwareActivity) activity).requestPermissions(permissions, REQUEST_CODE_PERMISSIONS, this);
        }
    }

    private void startTracking() {

        if (config != null) {
            GeolocationService.startTracking(getContext(), config);
            if (startPromise != null) {
                startPromise.resolve(true);
            }
        }
        else {
            Log.w("GeolocationModule", "Config is null. Do not start geo-tracking.");
        }
    }

    private void goToSettings() {

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getContext().getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getCurrentActivity().startActivity(intent);
    }

    private void missingPermission() {

        if (startPromise != null) {
            startPromise.reject("missing_permission", "Location permission denied");
        }
    }

    private void locationServiceDisabled() {

        if (startPromise != null) {
            startPromise.reject("location_disabled", "Location service is disabled");
        }
    }

    private Context getContext() {

        return getReactApplicationContext();
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_CODE_LOCATION_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                checkPermissionsAndStart();
            } else {
                locationServiceDisabled();
            }
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode != REQUEST_CODE_PERMISSIONS) {
            return false;
        }

        boolean allGranted = true;

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startTracking();
        }
        else {
            missingPermission();
        }

        return true;
    }
}
