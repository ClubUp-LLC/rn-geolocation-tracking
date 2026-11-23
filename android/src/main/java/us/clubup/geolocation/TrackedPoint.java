package us.clubup.geolocation;


final class TrackedPoint {

    static final long MINUTE_MS = 60_000L;

    final long timeMs;
    final double latitude;
    final double longitude;
    final float accuracy;
    final double altitude;

    TrackedPoint(long timeMs, double latitude, double longitude, float accuracy, double altitude) {

        this.timeMs = timeMs;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.altitude = altitude;
    }

    long clockMinute() {

        return timeMs / MINUTE_MS;
    }
}
