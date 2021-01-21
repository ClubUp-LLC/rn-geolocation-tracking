# react-native-geolocation-tracking

## Description
The module allows to start/stop geolocation tracking. When a new location is available the module submits it via HTTP Post request.

## Installation
`$ npm install react-native-geolocation-tracking --save`

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
2. Inside `<application>` node:
```xml
      <service
        android:name="us.clubup.geolocation.GeolocationService"
        android:exported="false"
        android:foregroundServiceType="location" />
```
3. Add Google Play Services maven address ([official native guide](https://developers.google.com/android/guides/setup)):

Open `android/build.gradle` file and make sure there is `google()` or `maven { url "https://maven.google.com" }` added to the `repositories` block.


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
iOS only uses `distanceFilter` to configure [CLLocationManager](https://developer.apple.com/documentation/corelocation/cllocationmanager) updates. Activity type is hardcoded to [CLActivityTypeOtherNavigation](https://developer.apple.com/documentation/corelocation/clactivitytype/othernavigation).
      

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