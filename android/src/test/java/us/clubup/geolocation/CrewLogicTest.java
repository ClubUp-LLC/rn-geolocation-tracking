// Run from android/: J=$(find ~/.gradle/caches -name 'json-2*.jar' | head -1); rm -rf /tmp/crew-test && javac -cp "$J" -d /tmp/crew-test src/test/java/us/clubup/geolocation/CrewLogicTest.java src/main/java/us/clubup/geolocation/{TrackedPoint,PointQueue,IsoTime,TrackingUntilKey,TrackingAnswer,UploadOutcome,CrewTracker}.java && java -cp "/tmp/crew-test:$J" us.clubup.geolocation.CrewLogicTest
package us.clubup.geolocation;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public final class CrewLogicTest {

    private static final long T0 = IsoTime.parse("2026-11-23T13:00:00Z");
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60_000L;
    private static final long STOP = T0 + 60 * MINUTE;
    private static final String KEEP_STOP = "{\"tracking_enabled\":true,\"tracking_until\":\"2026-11-23T14:00:00Z\"}";

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {

        // Crew mode switch: the trackingUntil key in the headers map
        run("no key keeps 1.1.2 mode and the headers", CrewLogicTest::noKeyKeepsLegacyModeAndHeaders);
        run("a readable key turns on crew mode and leaves the headers", CrewLogicTest::readableKeyIsTakenOut);
        run("a null key is crew mode with no stop time", CrewLogicTest::nullKeyIsTakenOut);
        run("an unreadable key is crew mode with no stop time", CrewLogicTest::unreadableKeyIsTakenOut);

        // ISO 8601
        run("parses Z, fractions and offsets", CrewLogicTest::parsesIsoTimes);
        run("rejects text that is not a zoned ISO time", CrewLogicTest::rejectsBadIsoTimes);

        // Queue
        run("a newer reading replaces one of the same minute", CrewLogicTest::newerReadingReplacesSameMinute);
        run("an older reading does not replace a newer one", CrewLogicTest::olderReadingKeepsNewer);
        run("readings of different minutes are all kept", CrewLogicTest::differentMinutesAreKept);
        run("dropFrom removes readings at or after the time", CrewLogicTest::dropFromRemovesAtOrAfter);

        // Status codes and answers
        run("status codes map to outcomes", CrewLogicTest::statusCodesMapToOutcomes);
        run("answers parse", CrewLogicTest::answersParse);

        // Due-to-send rule
        run("the first reading goes at once", CrewLogicTest::firstReadingGoesAtOnce);
        run("points wait 60 s after the last request", CrewLogicTest::pointsWaitSixtySeconds);
        run("an empty queue waits 2 minutes from start", CrewLogicTest::emptyQueueWaitsTwoMinutesFromStart);
        run("an empty queue waits 2 minutes from the last request", CrewLogicTest::emptyQueueWaitsTwoMinutesFromLastRequest);
        run("one upload at a time", CrewLogicTest::oneUploadAtATime);
        run("a stuck upload expires and keeps its points", CrewLogicTest::stuckUploadExpires);

        // Answers to an upload
        run("2xx with a later stop time moves the stop", CrewLogicTest::laterStopTimeKeepsTracking);
        run("2xx with an earlier stop time stops then", CrewLogicTest::earlierStopTimeStopsThen);
        run("2xx with a past stop time stops at the next tick", CrewLogicTest::pastStopTimeSendsOnceAndStops);
        run("2xx with a null stop time stops at once", CrewLogicTest::nullStopTimeStops);
        run("2xx with tracking off stops at once", CrewLogicTest::trackingOffStops);
        run("2xx with an unreadable body changes nothing", CrewLogicTest::unreadableBodyChangesNothing);
        run("4xx drops the batch", CrewLogicTest::clientErrorDropsBatch);
        run("5xx keeps the points", CrewLogicTest::serverErrorKeepsPoints);
        run("a network failure or timeout keeps the points", CrewLogicTest::networkFailureKeepsPoints);
        run("a late answer of an expired upload is ignored", CrewLogicTest::lateAnswerIsIgnored);

        // Stop time
        run("readings at or after the stop time are dropped", CrewLogicTest::readingsAtStopTimeAreDropped);
        run("at the stop time the rest goes once and tracking stops", CrewLogicTest::stopTimeSendsRestOnceThenStops);
        run("queued points past an earlier stop time are dropped", CrewLogicTest::queuedPointsPastEarlierStopAreDropped);
        run("the stop does not wait for an upload in flight", CrewLogicTest::stopDoesNotWaitForUpload);
        run("a stop from the app hands back the rest once", CrewLogicTest::stopFromAppSendsRestOnce);
        run("a new stop time from the app moves the stop", CrewLogicTest::appStopTimeMovesTheStop);

        // Alarms
        run("no 2-minute alarm within 10 minutes of the stop", CrewLogicTest::noHeartbeatNearStop);
        run("the 2-minute alarm follows the last request", CrewLogicTest::heartbeatFollowsLastRequest);
        run("the stop alarm is the stop time while it is ahead", CrewLogicTest::stopAlarmTimes);

        // Restart
        run("a restart resumes only before the saved stop time", CrewLogicTest::restartDecision);

        System.out.println(passed + " passed, " + failures.size() + " failed");
        for (String failure : failures) {
            System.out.println("FAILED: " + failure);
        }
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    // --- Crew mode switch

    private static void noKeyKeepsLegacyModeAndHeaders() {

        HashMap<String, Object> headers = headers();
        check(TrackingUntilKey.take(headers) == null, "no stop time");
        check(!TrackingUntilKey.isPresent(headers), "1.1.2 mode");
        check(headers.equals(headers()), "headers unchanged");
    }

    private static void readableKeyIsTakenOut() {

        HashMap<String, Object> headers = headers();
        headers.put(TrackingUntilKey.NAME, "2026-11-23T14:00:00Z");
        check(TrackingUntilKey.isPresent(headers), "crew mode");
        check(Long.valueOf(STOP).equals(TrackingUntilKey.take(headers)), "stop time");
        check(headers.equals(headers()), "key removed");
    }

    private static void nullKeyIsTakenOut() {

        HashMap<String, Object> headers = headers();
        headers.put(TrackingUntilKey.NAME, null);
        check(TrackingUntilKey.isPresent(headers), "a null key is still crew mode");
        check(TrackingUntilKey.take(headers) == null, "no stop time");
        check(!headers.containsKey(TrackingUntilKey.NAME), "key removed");
    }

    private static void unreadableKeyIsTakenOut() {

        HashMap<String, Object> headers = headers();
        headers.put(TrackingUntilKey.NAME, "tomorrow");
        check(TrackingUntilKey.isPresent(headers), "crew mode");
        check(TrackingUntilKey.take(headers) == null, "no stop time");
        check(!headers.containsKey(TrackingUntilKey.NAME), "key removed");
    }

    // --- ISO 8601

    private static void parsesIsoTimes() {

        check(Long.valueOf(STOP).equals(IsoTime.parse("2026-11-23T14:00:00Z")), "Z");
        check(Long.valueOf(STOP + 250).equals(IsoTime.parse("2026-11-23T14:00:00.25Z")), "fraction");
        check(Long.valueOf(STOP + 123).equals(IsoTime.parse("2026-11-23T14:00:00.123456Z")), "long fraction");
        check(Long.valueOf(STOP).equals(IsoTime.parse("2026-11-23T16:00:00+02:00")), "plus offset");
        check(Long.valueOf(STOP).equals(IsoTime.parse("2026-11-23T09:00:00-0500")), "minus offset");
        check(Long.valueOf(1_795_442_400_000L).equals(IsoTime.parse("2026-11-23T14:00:00Z")), "epoch value");
    }

    private static void rejectsBadIsoTimes() {

        check(IsoTime.parse(null) == null, "null");
        check(IsoTime.parse("") == null, "empty");
        check(IsoTime.parse("2026-11-23T14:00:00") == null, "no zone");
        check(IsoTime.parse("2026-13-23T14:00:00Z") == null, "month 13");
        check(IsoTime.parse("2026-11-23 14:00:00Z") == null, "space");
    }

    // --- Queue

    private static void newerReadingReplacesSameMinute() {

        PointQueue queue = new PointQueue();
        queue.add(point(T0 + 5 * SECOND));
        queue.add(point(T0 + 50 * SECOND));
        List<TrackedPoint> points = queue.takeAll();
        check(points.size() == 1, "one point");
        check(points.get(0).timeMs == T0 + 50 * SECOND, "the newer one");
        check(queue.isEmpty(), "takeAll empties");
    }

    private static void olderReadingKeepsNewer() {

        PointQueue queue = new PointQueue();
        queue.add(point(T0 + 50 * SECOND));
        queue.add(point(T0 + 5 * SECOND));
        check(queue.takeAll().get(0).timeMs == T0 + 50 * SECOND, "the newer one stays");
    }

    private static void differentMinutesAreKept() {

        PointQueue queue = new PointQueue();
        queue.add(point(T0 + MINUTE));
        queue.add(point(T0));
        List<TrackedPoint> points = queue.takeAll();
        check(points.size() == 2, "two points");
        check(points.get(0).timeMs == T0, "oldest first");
    }

    private static void dropFromRemovesAtOrAfter() {

        PointQueue queue = new PointQueue();
        queue.add(point(STOP - SECOND));
        queue.add(point(STOP + MINUTE));
        queue.dropFrom(STOP);
        List<TrackedPoint> before = queue.takeAll();
        check(before.size() == 1 && before.get(0).timeMs == STOP - SECOND, "keeps the earlier one");

        queue.add(point(STOP));
        queue.dropFrom(STOP);
        check(queue.isEmpty(), "drops the one at the stop time");
    }

    // --- Status codes and answers

    private static void statusCodesMapToOutcomes() {

        check(UploadOutcome.ofStatusCode(200) == UploadOutcome.ACCEPTED, "200");
        check(UploadOutcome.ofStatusCode(204) == UploadOutcome.ACCEPTED, "204");
        check(UploadOutcome.ofStatusCode(400) == UploadOutcome.REJECTED, "400");
        check(UploadOutcome.ofStatusCode(401) == UploadOutcome.REJECTED, "401");
        check(UploadOutcome.ofStatusCode(422) == UploadOutcome.REJECTED, "422");
        check(UploadOutcome.ofStatusCode(500) == UploadOutcome.FAILED, "500");
        check(UploadOutcome.ofStatusCode(503) == UploadOutcome.FAILED, "503");
        check(UploadOutcome.ofStatusCode(302) == UploadOutcome.FAILED, "302");
    }

    private static void answersParse() {

        TrackingAnswer keep = TrackingAnswer.parse(KEEP_STOP);
        check(keep.hasStopTime() && keep.stopTimeMs() == STOP, "true with a time");
        check(TrackingAnswer.parse("{\"tracking_enabled\":false,\"tracking_until\":\"2026-11-23T14:00:00Z\"}").isStop(), "false");
        check(TrackingAnswer.parse("{\"tracking_enabled\":true,\"tracking_until\":null}").isStop(), "null time");
        check(TrackingAnswer.parse("{\"tracking_enabled\":true}").isStop(), "missing time");
        check(TrackingAnswer.parse("<html>") == TrackingAnswer.UNREADABLE, "not JSON");
        check(TrackingAnswer.parse("[{\"latitude\":1}]") == TrackingAnswer.UNREADABLE, "the old points echo");
        check(TrackingAnswer.parse("{\"tracking_until\":\"2026-11-23T14:00:00Z\"}") == TrackingAnswer.UNREADABLE, "no flag");
        check(TrackingAnswer.parse("{\"tracking_enabled\":\"true\"}") == TrackingAnswer.UNREADABLE, "flag as text");
        check(TrackingAnswer.parse("{\"tracking_enabled\":true,\"tracking_until\":\"soon\"}") == TrackingAnswer.UNREADABLE,
                "unreadable time");
        check(TrackingAnswer.parse("") == TrackingAnswer.UNREADABLE, "empty");
        check(TrackingAnswer.parse(null) == TrackingAnswer.UNREADABLE, "null body");
    }

    // --- Due-to-send rule

    private static void firstReadingGoesAtOnce() {

        CrewTracker tracker = tracker();
        check(tracker.tick(T0 + SECOND).action == CrewTracker.Tick.Action.IDLE, "empty queue at start waits");
        tracker.addReading(point(T0 + 2 * SECOND));
        CrewTracker.Tick tick = tracker.tick(T0 + 3 * SECOND);
        check(tick.action == CrewTracker.Tick.Action.SEND, "sends");
        check(tick.batch.size() == 1, "with the reading");
    }

    private static void pointsWaitSixtySeconds() {

        CrewTracker tracker = trackerAfterUploadAt(T0);
        tracker.addReading(point(T0 + 30 * SECOND));
        check(tracker.tick(T0 + 59 * SECOND).action == CrewTracker.Tick.Action.IDLE, "59 s waits");
        check(tracker.tick(T0 + 60 * SECOND).action == CrewTracker.Tick.Action.SEND, "60 s sends");
    }

    private static void emptyQueueWaitsTwoMinutesFromStart() {

        CrewTracker tracker = tracker();
        check(tracker.tick(T0 + 2 * MINUTE - SECOND).action == CrewTracker.Tick.Action.IDLE, "under 2 min waits");
        CrewTracker.Tick tick = tracker.tick(T0 + 2 * MINUTE);
        check(tick.action == CrewTracker.Tick.Action.SEND && tick.batch.isEmpty(), "2 min sends an empty list");
    }

    private static void emptyQueueWaitsTwoMinutesFromLastRequest() {

        CrewTracker tracker = trackerAfterUploadAt(T0 + 5 * MINUTE);
        check(tracker.tick(T0 + 6 * MINUTE).action == CrewTracker.Tick.Action.IDLE, "1 min waits");
        check(tracker.tick(T0 + 7 * MINUTE - SECOND).action == CrewTracker.Tick.Action.IDLE, "just under 2 min waits");
        check(tracker.tick(T0 + 7 * MINUTE).action == CrewTracker.Tick.Action.SEND, "2 min sends");
    }

    private static void oneUploadAtATime() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        check(tracker.tick(T0).action == CrewTracker.Tick.Action.SEND, "first sends");
        tracker.addReading(point(T0 + MINUTE));
        check(tracker.isUploading(), "in flight");
        check(tracker.tick(T0 + MINUTE).action == CrewTracker.Tick.Action.IDLE, "second waits for the first");
    }

    private static void stuckUploadExpires() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        tracker.tick(T0);
        check(tracker.tick(T0 + CrewTracker.UPLOAD_EXPIRY_MS - SECOND).action == CrewTracker.Tick.Action.IDLE,
                "still waiting under the expiry");
        CrewTracker.Tick retry = tracker.tick(T0 + CrewTracker.UPLOAD_EXPIRY_MS);
        check(retry.action == CrewTracker.Tick.Action.SEND, "sends again at the expiry");
        check(retry.batch.size() == 1 && retry.batch.get(0).timeMs == T0, "with the stuck points");
    }

    // --- Answers to an upload

    private static void laterStopTimeKeepsTracking() {

        CrewTracker tracker = tracker();
        CrewTracker.Tick tick = sendEmptyAt(tracker, T0 + 2 * MINUTE);
        tracker.onUploadAnswered(tick.uploadId, 200, answerUntil("2026-11-23T16:00:00Z"));
        check(tracker.stopTimeMs() == T0 + 3 * 60 * MINUTE, "stop time moved");
        check(tracker.tick(STOP + MINUTE).action != CrewTracker.Tick.Action.STOP, "goes on past the old stop");
    }

    private static void earlierStopTimeStopsThen() {

        CrewTracker tracker = tracker();
        CrewTracker.Tick tick = sendEmptyAt(tracker, T0 + 2 * MINUTE);
        tracker.onUploadAnswered(tick.uploadId, 200, answerUntil("2026-11-23T13:30:00Z"));
        check(tracker.stopTimeMs() == T0 + 30 * MINUTE, "stop time moved earlier");
        check(tracker.tick(T0 + 29 * MINUTE).action != CrewTracker.Tick.Action.STOP, "tracks before it");
        check(tracker.tick(T0 + 30 * MINUTE).action == CrewTracker.Tick.Action.STOP, "stops at the new stop");
    }

    private static void pastStopTimeSendsOnceAndStops() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0 + 30 * SECOND));
        CrewTracker.Tick tick = tracker.tick(T0 + MINUTE);
        tracker.addReading(point(T0 + 70 * SECOND));
        tracker.onUploadAnswered(tick.uploadId, 200, answerUntil("2026-11-23T13:01:30Z"));
        CrewTracker.Tick last = tracker.tick(T0 + 2 * MINUTE);
        check(last.action == CrewTracker.Tick.Action.STOP, "stops at the next tick");
        check(last.batch.size() == 1 && last.batch.get(0).timeMs == T0 + 70 * SECOND, "with the rest to send once");
        check(tracker.tick(T0 + 3 * MINUTE).batch.isEmpty(), "and nothing more");
    }

    private static void nullStopTimeStops() {

        CrewTracker tracker = tracker();
        CrewTracker.Tick tick = sendEmptyAt(tracker, T0 + 2 * MINUTE);
        tracker.onUploadAnswered(tick.uploadId, 200, "{\"tracking_enabled\":true,\"tracking_until\":null}");
        tracker.addReading(point(T0 + 3 * MINUTE));
        check(tracker.tick(T0 + 3 * MINUTE).action == CrewTracker.Tick.Action.STOP, "stops");
        check(tracker.heartbeatAlarmAt(T0 + 3 * MINUTE) == null, "no 2-minute alarm");
        check(tracker.stopAlarmAt(T0 + 3 * MINUTE) == null, "no stop alarm");
    }

    private static void trackingOffStops() {

        CrewTracker tracker = tracker();
        CrewTracker.Tick tick = sendEmptyAt(tracker, T0 + 2 * MINUTE);
        tracker.onUploadAnswered(tick.uploadId, 200, "{\"tracking_enabled\":false,\"tracking_until\":null}");
        check(tracker.tick(T0 + 2 * MINUTE).action == CrewTracker.Tick.Action.STOP, "stops");
    }

    private static void unreadableBodyChangesNothing() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        CrewTracker.Tick tick = tracker.tick(T0);
        tracker.onUploadAnswered(tick.uploadId, 200, "[{\"latitude\":1}]");
        check(tracker.stopTimeMs() == STOP, "stop time unchanged");
        check(!tracker.isUploading(), "upload done");
        check(tracker.tick(T0 + 3 * MINUTE).action == CrewTracker.Tick.Action.SEND, "keeps tracking");
    }

    private static void clientErrorDropsBatch() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        CrewTracker.Tick tick = tracker.tick(T0);
        tracker.onUploadAnswered(tick.uploadId, 422, null);
        check(!tracker.isUploading(), "upload done");
        CrewTracker.Tick next = tracker.tick(T0 + 2 * MINUTE);
        check(next.action == CrewTracker.Tick.Action.SEND && next.batch.isEmpty(), "the batch is gone");
        check(tracker.stopTimeMs() == STOP, "stop time unchanged");
    }

    private static void serverErrorKeepsPoints() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        CrewTracker.Tick tick = tracker.tick(T0);
        tracker.onUploadAnswered(tick.uploadId, 503, KEEP_STOP);
        tracker.addReading(point(T0 + MINUTE));
        CrewTracker.Tick next = tracker.tick(T0 + MINUTE);
        check(next.action == CrewTracker.Tick.Action.SEND, "next tick sends");
        check(next.batch.size() == 2 && next.batch.get(0).timeMs == T0, "with the kept points");
    }

    private static void networkFailureKeepsPoints() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        CrewTracker.Tick tick = tracker.tick(T0);
        tracker.onUploadFailed(tick.uploadId);
        CrewTracker.Tick next = tracker.tick(T0 + MINUTE);
        check(next.action == CrewTracker.Tick.Action.SEND && next.batch.size() == 1, "sent again");
    }

    private static void lateAnswerIsIgnored() {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        CrewTracker.Tick stuck = tracker.tick(T0);
        CrewTracker.Tick retry = tracker.tick(T0 + CrewTracker.UPLOAD_EXPIRY_MS);
        tracker.onUploadAnswered(stuck.uploadId, 200, "{\"tracking_enabled\":false}");
        check(tracker.isUploading(), "the retry is still in flight");
        check(tracker.tick(T0 + 2 * MINUTE).action != CrewTracker.Tick.Action.STOP, "the late answer did not stop");
        tracker.onUploadAnswered(retry.uploadId, 200, KEEP_STOP);
        check(!tracker.isUploading(), "the retry's answer counts");
    }

    // --- Stop time

    private static void readingsAtStopTimeAreDropped() {

        CrewTracker tracker = trackerAfterUploadAt(STOP - 5 * MINUTE);
        tracker.addReading(point(STOP));
        tracker.addReading(point(STOP + 30 * SECOND));
        CrewTracker.Tick tick = tracker.tick(STOP - 3 * MINUTE);
        check(tick.action == CrewTracker.Tick.Action.SEND && tick.batch.isEmpty(), "nothing at or after the stop is kept");

        CrewTracker other = trackerAfterUploadAt(STOP - 5 * MINUTE);
        other.addReading(point(STOP - SECOND));
        check(other.tick(STOP - 3 * MINUTE).batch.size() == 1, "a reading just before the stop is kept");
    }

    private static void stopTimeSendsRestOnceThenStops() {

        CrewTracker tracker = trackerAfterUploadAt(STOP - 30 * SECOND);
        tracker.addReading(point(STOP - 10 * SECOND));
        CrewTracker.Tick last = tracker.tick(STOP);
        check(last.action == CrewTracker.Tick.Action.STOP, "stops at the stop time without waiting 60 s");
        check(last.batch.size() == 1, "with the rest");
        check(tracker.isStopped(), "stopped");
        tracker.addReading(point(STOP - 5 * SECOND));
        check(tracker.tick(STOP + SECOND).batch.isEmpty(), "takes no reading after the stop");
    }

    private static void queuedPointsPastEarlierStopAreDropped() {

        CrewTracker tracker = trackerAfterUploadAt(T0);
        tracker.addReading(point(T0 + 10 * MINUTE));
        tracker.addReading(point(T0 + 20 * MINUTE));
        tracker.changeStopTime(T0 + 15 * MINUTE);
        CrewTracker.Tick last = tracker.tick(T0 + 21 * MINUTE);
        check(last.batch.size() == 1 && last.batch.get(0).timeMs == T0 + 10 * MINUTE, "only the point before the stop");
    }

    private static void stopDoesNotWaitForUpload() {

        CrewTracker tracker = trackerAfterUploadAt(STOP - 3 * MINUTE);
        tracker.addReading(point(STOP - 90 * SECOND));
        CrewTracker.Tick inFlight = tracker.tick(STOP - 60 * SECOND);
        tracker.addReading(point(STOP - 30 * SECOND));
        check(tracker.isUploading(), "an upload is in flight");
        CrewTracker.Tick last = tracker.tick(STOP);
        check(last.action == CrewTracker.Tick.Action.STOP, "stops without waiting");
        check(last.batch.size() == 1 && last.batch.get(0).timeMs == STOP - 30 * SECOND, "sends only the queued rest");
        tracker.onUploadAnswered(inFlight.uploadId, 200, answerUntil("2026-11-23T16:00:00Z"));
        check(tracker.stopTimeMs() == STOP, "its late answer is ignored");
        check(tracker.tick(STOP + SECOND).action == CrewTracker.Tick.Action.STOP, "and does not restart tracking");
    }

    private static void stopFromAppSendsRestOnce() {

        CrewTracker tracker = trackerAfterUploadAt(T0 + MINUTE);
        tracker.addReading(point(T0 + 90 * SECOND));
        List<TrackedPoint> rest = tracker.stop();
        check(rest.size() == 1, "hands back the queued points");
        check(tracker.tick(T0 + 2 * MINUTE).action == CrewTracker.Tick.Action.STOP, "stays stopped");
        check(tracker.heartbeatAlarmAt(T0 + 2 * MINUTE) == null && tracker.stopAlarmAt(T0 + 2 * MINUTE) == null, "no alarms");
    }

    private static void appStopTimeMovesTheStop() {

        CrewTracker tracker = trackerAfterUploadAt(STOP - 5 * MINUTE);
        tracker.changeStopTime(STOP + 60 * MINUTE);
        check(tracker.tick(STOP).action != CrewTracker.Tick.Action.STOP, "goes on past the old stop");
        check(tracker.tick(STOP + 60 * MINUTE).action == CrewTracker.Tick.Action.STOP, "stops at the new one");
    }


    // --- Alarms

    private static void noHeartbeatNearStop() {

        CrewTracker tracker = trackerAfterUploadAt(STOP - 12 * MINUTE);
        check(tracker.heartbeatAlarmAt(STOP - 10 * MINUTE) != null, "10 minutes away sets one");
        check(tracker.heartbeatAlarmAt(STOP - 10 * MINUTE + SECOND) == null, "under 10 minutes sets none");
    }

    private static void heartbeatFollowsLastRequest() {

        check(Long.valueOf(T0 + 2 * MINUTE).equals(tracker().heartbeatAlarmAt(T0)), "2 min after the start");
        CrewTracker tracker = trackerAfterUploadAt(T0 + 5 * MINUTE);
        check(Long.valueOf(T0 + 7 * MINUTE).equals(tracker.heartbeatAlarmAt(T0 + 6 * MINUTE)), "2 min after the request");
        check(Long.valueOf(T0 + 9 * MINUTE).equals(tracker.heartbeatAlarmAt(T0 + 7 * MINUTE)), "never in the past");
    }

    private static void stopAlarmTimes() {

        CrewTracker tracker = trackerAfterUploadAt(STOP - 5 * MINUTE);
        check(Long.valueOf(STOP).equals(tracker.stopAlarmAt(STOP - MINUTE)), "the stop time");
        check(tracker.stopAlarmAt(STOP) == null, "none once the stop time is reached");
    }


    // --- Restart

    private static void restartDecision() {

        check(CrewTracker.canResume(STOP, STOP - SECOND), "future stop resumes");
        check(!CrewTracker.canResume(STOP, STOP), "stop time reached stops");
        check(!CrewTracker.canResume(STOP, STOP + MINUTE), "passed stop stops");
        check(!CrewTracker.canResume(null, T0), "nothing saved stops");
    }

    // --- Helpers

    private static CrewTracker tracker() {

        return new CrewTracker(STOP, T0);
    }

    private static CrewTracker trackerAfterUploadAt(long timeMs) {

        CrewTracker tracker = tracker();
        tracker.addReading(point(T0));
        CrewTracker.Tick tick = tracker.tick(timeMs);
        check(tick.action == CrewTracker.Tick.Action.SEND, "setup upload");
        tracker.onUploadAnswered(tick.uploadId, 200, KEEP_STOP);
        return tracker;
    }

    private static CrewTracker.Tick sendEmptyAt(CrewTracker tracker, long timeMs) {

        CrewTracker.Tick tick = tracker.tick(timeMs);
        check(tick.action == CrewTracker.Tick.Action.SEND, "setup empty upload");
        return tick;
    }

    private static String answerUntil(String isoTime) {

        return "{\"tracking_enabled\":true,\"tracking_until\":\"" + isoTime + "\"}";
    }

    private static TrackedPoint point(long timeMs) {

        return new TrackedPoint(timeMs, 40.7128, -74.006, 12f, 8.0);
    }

    private static HashMap<String, Object> headers() {

        HashMap<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "token");
        headers.put("Accept", "application/json");
        return headers;
    }

    private static void check(boolean condition, String what) {

        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void run(String name, Runnable test) {

        try {
            test.run();
            passed++;
        }
        catch (AssertionError | RuntimeException e) {
            failures.add(name + ": " + e.getMessage());
        }
    }
}
