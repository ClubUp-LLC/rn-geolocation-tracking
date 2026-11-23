package us.clubup.geolocation;


enum UploadOutcome {

    ACCEPTED,
    REJECTED,
    FAILED;

    static UploadOutcome ofStatusCode(int statusCode) {

        if (statusCode >= 200 && statusCode < 300) {
            return ACCEPTED;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return REJECTED;
        }
        return FAILED;
    }
}
