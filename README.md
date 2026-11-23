# react-native-geolocation-tracking

## Description
The module allows to start/stop geolocation tracking. When a new location is available the module submits it via HTTP Post request.

## Installation
Add the following to `dependencies` block inside package.json
```
    "react-native-geolocation-tracking": "https://github.com/ClubUp-LLC/rn-geolocation-tracking",
```

Then you need to perform a few manual steps described below.
#### iOS
Add location usage description: 

Open `ios/<main_target_folder>/Info.plist`, add to the root `<dict>` node the following (if these items don't exist yet):
```
	<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
	<string>App needs to know your location</string>
	<key>NSLocationAlwaysUsageDescription</key>
	<string>App needs to know your location</string>
	<key>NSLocationWhenInUseUsageDescription</key>
	<string>App needs to know your location</string>
	
	<key>UIBackgroundModes</key>
	<array>
		<string>location</string>
	</array>
```
Notes: 
1. Consider changing the content of `<string>` to something that makes sense for the user.
2. If `UIBackgroundModes` already present in Info.plist file, add `location` to the existing array.

#### Android
Add to AndroidManifest.xml (if these lines do not exist yet):

1. Inside `<manifest>` node: 
```xml
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```
The library manifest adds the `WAKE_LOCK` permission itself (new in 1.2.0). Crew mode needs it, and your app does not have to declare it.
2. Inside `<application>` node:
```xml
      <service
        android:name="us.clubup.geolocation.GeolocationService"
        android:exported="false"
        android:foregroundServiceType="location" />
```
3. Add Google Play Services maven address ([official native guide](https://developers.google.com/android/guides/setup)):

Open `android/build.gradle` file and make sure there is `google()` or `maven { url "https://maven.google.com" }` added to the `repositories` block.

Optionally you can change the notification icon color by defining `notification_accent` color. To do this, open (or create)  `android/<app_main_module>/src/res/values/colors.xml` and add `<color name="notification_accent">#16CA93</color>` to the `resources` node. So the file looks like the following:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="notification_accent">#16CA93</color>
</resources>
```
Here `#16CA93` is a HEX RGB representation of the color.

## Usage
```javascript
import { NativeModules } from 'react-native';

const { Geolocation } = NativeModules;

function* startTrackingFlow() {
  if (!Geolocation) {
    console.log('Geolocation module not found');
    return;
  }

  try {
    const updatesIntervalSeconds = 5 * 60; // 5 minutes
    const distanceFilter = 10;
    const trackingUrl = `${APP_HOST}/api/locations`;

    const token = yield call(getAuthToken);

    const params = { Authorization: token };

    yield call(
      Geolocation.startTracking,
      trackingUrl,
      params,
      updatesIntervalSeconds,
      distanceFilter
    );
  } catch (e) {
    showAlert('Cannot start tracking', e.message);
  }
}

function* stopTrackingFlow() {
  if (!Geolocation) {
    console.log('Geolocation module not found');
    return;
  }

  try {
    yield call(Geolocation.stopTracking);
  } catch (e) {
    showAlert('Cannot stop tracking', e.message);
  }
}  
```

As you can see in the code above, `startTracking()` expects the following parameters:
- `trackingUrl` - URL for HTTP post requests where locations should be submitted
- `params` - an object that is used to construct HTTP-headers. Typically you will want to pass `Authorization` header there.
- `distanceFilter` - number of meters between locations to prevent too frequent updates.
- `updatesIntervalSeconds` - amount of seconds that should be passed between the location updates. Used only on Android.

##### Android specific
In Android both `updatesIntervalSeconds` and `distanceFilter` should pass between location updates. So if only the distance since last coordinate is more than `distanceFilter`, but the last update was less than `updatesIntervalSeconds`, the location update will not be provided by Android OS.

##### iOS specific
iOS only uses `distanceFilter` to configure [CLLocationManager](https://developer.apple.com/documentation/corelocation/cllocationmanager) updates. Activity type is hardcoded to [CLActivityTypeOtherNavigation](https://developer.apple.com/documentation/corelocation/clactivitytype/othernavigation). The configuration is [here](https://github.com/ClubUp-LLC/rn-geolocation-tracking/blob/main/ios/GeolocationService.m#L40).
      
### HTTP payload
Example of HTTP payload:
```
{
  "points": [
    {
      "time": "2020-12-15T09:45:26Z",
      "latitude": 53.2032983,
      "longitude": 50.1477,
      "accuracy": 20,
      "altitude": 0
    }
  ]
}
```

## Crew mode (1.2.0)
Crew mode is for tracking a shift that ends at a known time. It is optional. You turn it on by adding one key to the headers map.

### Turning it on
Pass `trackingUntil` in the headers map, which is the third argument of `startTracking`:
```javascript
const headers = {
  Authorization: token,
  trackingUntil: '2026-11-23T21:00:00Z',
};
```
- `trackingUntil` is an ISO 8601 UTC string. It is the time tracking must stop.
- The library removes the key before it sends the headers. The server never sees it.
- Without the key, the library works as in 1.1.2 (see "Without the key" below).

### What crew mode does
Both platforms:
- Distance filter: 50 m. The `distanceFilter` argument is ignored.
- At most one point is kept per minute.
- Points are uploaded in batches. A tick runs every 60 seconds and sends the points that are waiting.
- If no request was sent for 2 minutes, the tick sends an empty upload.
- Only one upload runs at a time. Each upload has a 30-second timeout.
- If an upload fails, the library keeps the points and sends them with the next batch.
- If the server answers with a 4xx status, the library drops that batch.
- The server can answer with `{ "tracking_enabled": true, "tracking_until": "<ISO 8601 UTC>" }`. A new `tracking_until` moves the stop time. `"tracking_enabled": false` ends tracking.
- The library stops by itself at the stop time. It sends the points taken before that time once, then stops.

Accuracy and interval by platform:
- iOS: about 10 m accuracy (`kCLLocationAccuracyNearestTenMeters`).
- Android: a location request every 60 seconds (`updatesIntervalSeconds` is ignored).

### Android notes
- The service holds a short wake lock for each tick and its upload. It does not hold one for the whole shift.
- The tick, the empty upload and the stop use `setAndAllowWhileIdle` alarms.
- In Doze mode these alarms run about every 9 minutes. A phone that lies still may stop up to about 10 minutes after the stop time.
- The library manifest declares the `WAKE_LOCK` permission and the alarm receiver.
- The library saves the stop time and the headers in SharedPreferences. If the system restarts the service, it continues with the saved values. It does not restart after the stop time.

### Without the key
Without `trackingUntil`, the library behaves as 1.1.2. There is one change on Android: `stopTracking` now really stops the service. In 1.1.2 it removed the notification but left the service running.

### Logic tests
The crew mode rules have tests that run on a computer. They do not need a phone or an emulator.
- iOS (macOS with Xcode command line tools): `bash ios/Tests/run.sh`
- Android (plain Java, no Gradle): run the command on the first line of `android/src/test/java/us/clubup/geolocation/CrewLogicTest.java`, from the `android/` folder. It needs `javac` and an `org.json` jar from the Gradle cache.

The podspec excludes `ios/Tests/**`, so the iOS tests are never built into an app. Gradle treats `android/src/test` as the unit-test source set, so the Android test is not built into an app either.

## Changelog
### 1.2.0
- New: crew mode, turned on with the `trackingUntil` key in the headers map.
- New: the library stops by itself at the stop time, and the server can change it or end tracking.
- New (Android): `WAKE_LOCK` permission in the library manifest.
- Fixed (Android): `stopTracking` now stops the service. Before, it only removed the notification.
- Changed: the iOS test harness (`ios/Tests`) is excluded from the pod.
- No change for apps that do not pass `trackingUntil`, apart from the Android fix.
