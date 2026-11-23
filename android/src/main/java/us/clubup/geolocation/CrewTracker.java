package us.clubup.geolocation;


import java.util.Collections;
import java.util.List;


/**
 * Crew mode decisions with no Android in them: which readings to keep, when a tick uploads,
 * what an upload answer changes, and when tracking stops. The service calls it on its main thread.
 */
final class CrewTracker {

    static final long MIN_UPLOAD_GAP_MS = 60_000L;
    static final long EMPTY_UPLOAD_GAP_MS = 2 * 60_000L;
    // Doze runs this app's idle alarms about once per 9 minutes, and the stop alarm must get that slot.
    static final long HEARTBEAT_CUTOFF_MS = 10 * 60_000L;
    // Three times the network timeouts: a DNS lookup ignores them and must not block every later upload.
    static final long UPLOAD_EXPIRY_MS = 90_000L;

    private final PointQueue queue = new PointQueue();
    private final long startedAtMs;
    private long stopTimeMs;

    private boolean hasRequested;
    // 0 until the first request, so the first reading goes at once.
    private long lastRequestAtMs;

    private List<TrackedPoint> uploadingBatch;
    private long uploadStartedAtMs;
    private int uploadId;

    private boolean stopped;

    CrewTracker(long stopTimeMs, long nowMs) {

        this.stopTimeMs = stopTimeMs;
        this.startedAtMs = nowMs;
    }

    /** A service the system restarted resumes only with a saved stop time still ahead. */
    static boolean canResume(Long savedStopTimeMs, long nowMs) {

        return savedStopTimeMs != null && nowMs < savedStopTimeMs;
    }

    void addReading(TrackedPoint point) {

        if (point.timeMs < stopTimeMs) {
            queue.add(point);
        }
    }

    void changeStopTime(long newStopTimeMs) {

        stopTimeMs = newStopTimeMs;
    }

    Tick tick(long nowMs) {

        if (stopped) {
            return Tick.stop(Collections.<TrackedPoint>emptyList());
        }
        if (nowMs >= stopTimeMs) {
            return Tick.stop(stop());
        }
        expireStuckUpload(nowMs);
        if (isUploading()) {
            return Tick.IDLE;
        }
        return isUploadDue(nowMs) ? startUpload(nowMs) : Tick.IDLE;
    }

    /** Stops tracking and hands back the points before the stop time, to send once. */
    List<TrackedPoint> stop() {

        stopped = true;
        uploadingBatch = null;
        queue.dropFrom(stopTimeMs);
        return queue.takeAll();
    }

    void onUploadFailed(int id) {

        if (!isCurrentUpload(id)) {
            return;
        }
        queue.addAll(uploadingBatch);
        uploadingBatch = null;
    }

    void onUploadAnswered(int id, int statusCode, String body) {

        if (!isCurrentUpload(id)) {
            return;
        }
        UploadOutcome outcome = UploadOutcome.ofStatusCode(statusCode);
        if (outcome == UploadOutcome.FAILED) {
            onUploadFailed(id);
            return;
        }
        uploadingBatch = null;
        if (outcome == UploadOutcome.ACCEPTED) {
            apply(TrackingAnswer.parse(body));
        }
    }

    /** When the next empty-upload alarm should fire, or null for none. */
    Long heartbeatAlarmAt(long nowMs) {

        if (stopped || stopTimeMs - nowMs < HEARTBEAT_CUTOFF_MS) {
            return null;
        }
        long dueAtMs = lastContactAtMs() + EMPTY_UPLOAD_GAP_MS;
        return dueAtMs > nowMs ? dueAtMs : nowMs + EMPTY_UPLOAD_GAP_MS;
    }

    /** When the alarm that checks the stop time should fire, or null for none. */
    Long stopAlarmAt(long nowMs) {

        return !stopped && stopTimeMs > nowMs ? stopTimeMs : null;
    }

    boolean isUploading() {

        return uploadingBatch != null;
    }

    boolean isStopped() {

        return stopped;
    }

    long stopTimeMs() {

        return stopTimeMs;
    }

    private boolean isUploadDue(long nowMs) {

        if (queue.isEmpty()) {
            return nowMs - lastContactAtMs() >= EMPTY_UPLOAD_GAP_MS;
        }
        return nowMs - lastRequestAtMs >= MIN_UPLOAD_GAP_MS;
    }

    private Tick startUpload(long nowMs) {

        uploadingBatch = queue.takeAll();
        uploadStartedAtMs = nowMs;
        lastRequestAtMs = nowMs;
        hasRequested = true;
        uploadId++;
        return Tick.send(uploadId, uploadingBatch);
    }

    private void expireStuckUpload(long nowMs) {

        if (isUploading() && nowMs - uploadStartedAtMs >= UPLOAD_EXPIRY_MS) {
            onUploadFailed(uploadId);
        }
    }

    private void apply(TrackingAnswer answer) {

        if (answer.isStop()) {
            stopped = true;
        }
        else if (answer.hasStopTime()) {
            changeStopTime(answer.stopTimeMs());
        }
    }

    private boolean isCurrentUpload(int id) {

        return isUploading() && id == uploadId;
    }

    private long lastContactAtMs() {

        return hasRequested ? lastRequestAtMs : startedAtMs;
    }

    static final class Tick {

        static final Tick IDLE = new Tick(Action.IDLE, 0, Collections.<TrackedPoint>emptyList());

        enum Action { IDLE, SEND, STOP }

        final Action action;
        final int uploadId;
        /** SEND: the batch to upload. STOP: the last points, to send once with no retry. */
        final List<TrackedPoint> batch;

        private Tick(Action action, int uploadId, List<TrackedPoint> batch) {

            this.action = action;
            this.uploadId = uploadId;
            this.batch = batch;
        }

        static Tick send(int uploadId, List<TrackedPoint> batch) {

            return new Tick(Action.SEND, uploadId, batch);
        }

        static Tick stop(List<TrackedPoint> lastPoints) {

            return new Tick(Action.STOP, 0, lastPoints);
        }
    }
}
