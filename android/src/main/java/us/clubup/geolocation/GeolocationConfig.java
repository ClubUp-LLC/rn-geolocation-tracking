package us.clubup.geolocation;


import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.location.LocationRequest;

import java.util.HashMap;

import androidx.annotation.Nullable;


public class GeolocationConfig implements Parcelable {

    @Nullable
    private String uploadUrl;
    @Nullable
    private String failureUrl;
    @Nullable
    private HashMap<String, Object> headers;

    private int updatesIntervalSeconds = 5 * 60; // default to 5 minutes
    private int distanceFilter = 0;

    public GeolocationConfig(
            @Nullable String uploadUrl,
            @Nullable String failureUrl,
            @Nullable HashMap<String, Object> headers,
            int updatesIntervalSeconds,
            int distanceFilter) {

        this.uploadUrl = uploadUrl;
        this.failureUrl = failureUrl;
        this.headers = headers;
        this.updatesIntervalSeconds = updatesIntervalSeconds;
        this.distanceFilter = distanceFilter;
    }

    public GeolocationConfig(@Nullable String uploadUrl, @Nullable String failureUrl, @Nullable HashMap<String, Object> headers) {

        this.uploadUrl = uploadUrl;
        this.failureUrl = failureUrl;
        this.headers = headers;
    }

    protected GeolocationConfig(Parcel in) {

        uploadUrl = in.readString();
        failureUrl = in.readString();
        updatesIntervalSeconds = in.readInt();
        distanceFilter = in.readInt();
        headers = (HashMap<String, Object>) in.readSerializable();
    }

    @Nullable
    public String getUploadUrl() {

        return uploadUrl;
    }

    public void setUploadUrl(@Nullable String uploadUrl) {

        this.uploadUrl = uploadUrl;
    }

    @Nullable
    public String getFailureUrl() {

        return failureUrl;
    }

    public void setFailureUrl(@Nullable String failureUrl) {

        this.failureUrl = failureUrl;
    }

    @Nullable
    public HashMap<String, Object> getHeaders() {

        return headers;
    }

    public void setHeaders(@Nullable HashMap<String, Object> headers) {

        this.headers = headers;
    }

    public int getUpdatesIntervalSeconds() {

        return updatesIntervalSeconds;
    }

    public void setUpdatesIntervalSeconds(int updatesIntervalSeconds) {

        this.updatesIntervalSeconds = updatesIntervalSeconds;
    }

    public LocationRequest toLocationRequest() {

        return LocationRequest.create()
                              .setInterval(getUpdatesIntervalSeconds() * 1000)
                              .setFastestInterval(getUpdatesIntervalSeconds() * 1000)
                              .setSmallestDisplacement(distanceFilter)
                              .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(uploadUrl);
        dest.writeString(failureUrl);
        dest.writeInt(updatesIntervalSeconds);
        dest.writeInt(distanceFilter);
        dest.writeSerializable(headers);
    }

    @Override
    public int describeContents() {

        return 0;
    }

    public static final Creator<GeolocationConfig> CREATOR = new Creator<GeolocationConfig>() {

        @Override
        public GeolocationConfig createFromParcel(Parcel in) {

            return new GeolocationConfig(in);
        }

        @Override
        public GeolocationConfig[] newArray(int size) {

            return new GeolocationConfig[size];
        }
    };
}
