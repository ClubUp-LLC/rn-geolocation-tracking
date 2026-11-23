// Run from the repo root: ios/Tests/run.sh
// Objective-C++ (.mm) so the podspec's ios/**/*.m glob never builds it into an app.
#import <Foundation/Foundation.h>

#import "CrewTrackingPointQueue.h"
#import "CrewTrackingSession.h"
#import "CrewTrackingTime.h"
#import "CrewTrackingUploadResult.h"
#import "TrackingStartOptions.h"

static int failedChecks = 0;
static int checks = 0;

#define CHECK(condition) do { \
  checks++; \
  if (!(condition)) { failedChecks++; NSLog(@"FAIL %s:%d %s", __FILE__, __LINE__, #condition); } \
} while (0)

static NSDate *At(NSString *iso) { return CrewTrackingDateFromISO8601(iso); }

static CrewTrackingPoint *PointAt(NSString *iso) {
  return [[CrewTrackingPoint alloc] initWithTakenAt:At(iso) payload:@{@"time": iso}];
}

static NSArray<NSString *> *TimesOf(NSArray<CrewTrackingPoint *> *points) {
  return [points valueForKeyPath:@"payload.time"];
}

static CrewTrackingUploadResult *Answer(NSInteger status, NSString *body) {
  return [[CrewTrackingUploadResult alloc] initWithStatusCode:status
                                                         body:[body dataUsingEncoding:NSUTF8StringEncoding]
                                                        error:nil];
}

static CrewTrackingUploadResult *NetworkError(NSInteger code) {
  NSError *error = [NSError errorWithDomain:NSURLErrorDomain code:code userInfo:nil];
  return [[CrewTrackingUploadResult alloc] initWithStatusCode:0 body:nil error:error];
}

static CrewTrackingSession *SessionUntil(NSString *stopIso) {
  return [[CrewTrackingSession alloc] initWithStartTime:At(@"2026-11-23T09:00:00Z") stopTime:At(stopIso)];
}

static void TestIso8601Parsing(void) {
  CHECK([At(@"2026-11-23T19:00:00Z") isEqualToDate:[NSDate dateWithTimeIntervalSince1970:1795460400]]);
  CHECK([At(@"2026-11-23T19:00:00.000Z") isEqualToDate:At(@"2026-11-23T19:00:00Z")]);
  CHECK([At(@"2026-11-23T14:00:00-05:00") isEqualToDate:At(@"2026-11-23T19:00:00Z")]);
  CHECK(At(@"tomorrow") == nil);
  CHECK(CrewTrackingDateFromISO8601([NSNull null]) == nil);
  CHECK(CrewTrackingDateFromISO8601(@42) == nil);
}

static void TestStartOptions(void) {
  NSDictionary *legacyParams = @{@"Authorization": @"token", @"Accept": @"application/json"};
  TrackingStartOptions *legacy = [[TrackingStartOptions alloc] initWithParams:legacyParams];
  CHECK(!legacy.crewMode);
  CHECK([legacy.headers isEqualToDictionary:legacyParams]);
  CHECK(legacy.stopTime == nil);

  TrackingStartOptions *crew = [[TrackingStartOptions alloc] initWithParams:@{
    @"Authorization": @"token", TrackingUntilKey: @"2026-11-23T19:00:00Z"}];
  CHECK(crew.crewMode);
  CHECK([crew.headers isEqualToDictionary:@{@"Authorization": @"token"}]);
  CHECK([crew.stopTime isEqualToDate:At(@"2026-11-23T19:00:00Z")]);

  TrackingStartOptions *crewNull = [[TrackingStartOptions alloc] initWithParams:@{TrackingUntilKey: [NSNull null]}];
  CHECK(crewNull.crewMode);
  CHECK(crewNull.stopTime == nil);
  CHECK(crewNull.headers.count == 0);

  TrackingStartOptions *crewGarbage = [[TrackingStartOptions alloc] initWithParams:@{TrackingUntilKey: @"soon"}];
  CHECK(crewGarbage.crewMode);
  CHECK(crewGarbage.stopTime == nil);
}

static void TestQueueKeepsOnePointPerMinute(void) {
  CrewTrackingPointQueue *queue = [CrewTrackingPointQueue new];
  CHECK(queue.isEmpty);
  [queue addPoint:PointAt(@"2026-11-23T09:02:10Z")];
  [queue addPoint:PointAt(@"2026-11-23T09:01:05Z")];
  [queue addPoint:PointAt(@"2026-11-23T09:01:40Z")];
  [queue addPoint:PointAt(@"2026-11-23T09:01:20Z")];
  CHECK(!queue.isEmpty);
  NSArray *expected = @[@"2026-11-23T09:01:40Z", @"2026-11-23T09:02:10Z"];
  CHECK([TimesOf([queue takeAllPoints]) isEqualToArray:expected]);
  CHECK(queue.isEmpty);
}

static void TestQueueHandsOverPointsOldestFirst(void) {
  CrewTrackingPointQueue *queue = [CrewTrackingPointQueue new];
  NSMutableArray<NSString *> *expected = [NSMutableArray new];
  for (int minute = 59; minute >= 0; minute -= 7) {
    [expected insertObject:[NSString stringWithFormat:@"2026-11-23T09:%02d:00Z", minute] atIndex:0];
    [queue addPoint:PointAt(expected.firstObject)];
  }
  [queue addPoint:PointAt(@"2026-11-24T09:00:00Z")];
  [queue addPoint:PointAt(@"2026-11-22T09:00:00Z")];
  [expected insertObject:@"2026-11-22T09:00:00Z" atIndex:0];
  [expected addObject:@"2026-11-24T09:00:00Z"];
  CHECK([TimesOf([queue takeAllPoints]) isEqualToArray:expected]);
}

static void TestQueueRestoreKeepsNewerPoint(void) {
  CrewTrackingPointQueue *queue = [CrewTrackingPointQueue new];
  [queue addPoint:PointAt(@"2026-11-23T09:01:50Z")];
  [queue restorePoints:@[PointAt(@"2026-11-23T09:00:30Z"), PointAt(@"2026-11-23T09:01:10Z")]];
  NSArray *expected = @[@"2026-11-23T09:00:30Z", @"2026-11-23T09:01:50Z"];
  CHECK([TimesOf([queue takeAllPoints]) isEqualToArray:expected]);
}

static void TestUploadOutcomes(void) {
  CHECK(Answer(200, @"{}").outcome == CrewUploadOutcomeAccepted);
  CHECK(Answer(201, @"{}").outcome == CrewUploadOutcomeAccepted);
  CHECK(Answer(400, @"").outcome == CrewUploadOutcomeRejected);
  CHECK(Answer(401, @"").outcome == CrewUploadOutcomeRejected);
  CHECK(Answer(422, @"").outcome == CrewUploadOutcomeRejected);
  CHECK(Answer(499, @"").outcome == CrewUploadOutcomeRejected);
  CHECK(Answer(500, @"").outcome == CrewUploadOutcomeFailed);
  CHECK(Answer(503, @"").outcome == CrewUploadOutcomeFailed);
  CHECK(Answer(302, @"").outcome == CrewUploadOutcomeFailed);
  CHECK(NetworkError(NSURLErrorTimedOut).outcome == CrewUploadOutcomeFailed);
  CHECK(NetworkError(NSURLErrorNotConnectedToInternet).outcome == CrewUploadOutcomeFailed);
}

static void TestAnswerParsing(void) {
  CrewTrackingUploadResult *later = Answer(200, @"{\"tracking_enabled\":true,\"tracking_until\":\"2026-11-23T21:00:00Z\"}");
  CHECK(later.answerKind == CrewAnswerKindStopAt);
  CHECK([later.stopTime isEqualToDate:At(@"2026-11-23T21:00:00Z")]);

  CHECK(Answer(200, @"{\"tracking_enabled\":true,\"tracking_until\":null}").answerKind == CrewAnswerKindStopNow);
  CHECK(Answer(200, @"{\"tracking_enabled\":false,\"tracking_until\":null}").answerKind == CrewAnswerKindStopNow);
  CHECK(Answer(200, @"{\"tracking_enabled\":false,\"tracking_until\":\"2026-11-23T21:00:00Z\"}").answerKind == CrewAnswerKindStopNow);

  CHECK(Answer(200, @"not json").answerKind == CrewAnswerKindUnreadable);
  CHECK(Answer(200, @"").answerKind == CrewAnswerKindUnreadable);
  CHECK(Answer(200, @"[]").answerKind == CrewAnswerKindUnreadable);
  CHECK(Answer(200, @"{\"tracking_enabled\":true}").answerKind == CrewAnswerKindUnreadable);
  CHECK(Answer(200, @"{\"tracking_enabled\":true,\"tracking_until\":\"soon\"}").answerKind == CrewAnswerKindUnreadable);
  CHECK(Answer(422, @"{\"tracking_enabled\":false}").answerKind == CrewAnswerKindUnreadable);
  CHECK(Answer(500, @"{\"tracking_enabled\":false}").answerKind == CrewAnswerKindUnreadable);
}

static void TestEmptyUploadAfterTwoMinutes(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  CHECK(![session isUploadDueAt:At(@"2026-11-23T09:01:00Z")]);
  CHECK(![session isUploadDueAt:At(@"2026-11-23T09:01:54Z")]);
  CHECK([session isUploadDueAt:At(@"2026-11-23T09:01:59Z")]);
  CHECK([session isUploadDueAt:At(@"2026-11-23T09:02:00Z")]);
  CHECK([session beginUploadAt:At(@"2026-11-23T09:02:00Z")].count == 0);
  [session finishUploadWithResult:Answer(200, @"{}")];
  CHECK(![session isUploadDueAt:At(@"2026-11-23T09:03:00Z")]);
  CHECK([session isUploadDueAt:At(@"2026-11-23T09:04:00Z")]);
}

static void TestPointsUploadOncePerTick(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T09:00:20Z")];
  CHECK(![session isUploadDueAt:At(@"2026-11-23T09:00:30Z")]);
  CHECK(![session isUploadDueAt:At(@"2026-11-23T09:00:54Z")]);
  CHECK([session isUploadDueAt:At(@"2026-11-23T09:00:59Z")]);
  CHECK([session isUploadDueAt:At(@"2026-11-23T09:01:00Z")]);
}

static void TestOneUploadInFlight(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T09:00:20Z")];
  CHECK([TimesOf([session beginUploadAt:At(@"2026-11-23T09:01:00Z")]) isEqualToArray:@[@"2026-11-23T09:00:20Z"]]);
  [session addPoint:PointAt(@"2026-11-23T09:01:30Z")];
  CHECK(![session isUploadDueAt:At(@"2026-11-23T09:05:00Z")]);
  [session finishUploadWithResult:Answer(200, @"{}")];
  CHECK([session isUploadDueAt:At(@"2026-11-23T09:05:00Z")]);
}

static void TestFailedUploadKeepsPoints(void) {
  NSArray<CrewTrackingUploadResult *> *failedResults = @[Answer(500, @""), Answer(503, @""),
                                                         NetworkError(NSURLErrorTimedOut),
                                                         NetworkError(NSURLErrorNotConnectedToInternet)];
  for (CrewTrackingUploadResult *failure in failedResults) {
    CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
    [session addPoint:PointAt(@"2026-11-23T09:00:20Z")];
    [session beginUploadAt:At(@"2026-11-23T09:01:00Z")];
    [session addPoint:PointAt(@"2026-11-23T09:01:30Z")];
    [session finishUploadWithResult:failure];
    NSArray *expected = @[@"2026-11-23T09:00:20Z", @"2026-11-23T09:01:30Z"];
    CHECK([TimesOf([session beginUploadAt:At(@"2026-11-23T09:02:00Z")]) isEqualToArray:expected]);
  }
}

static void TestRejectedUploadDropsBatch(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T09:00:20Z")];
  [session beginUploadAt:At(@"2026-11-23T09:01:00Z")];
  [session addPoint:PointAt(@"2026-11-23T09:01:30Z")];
  [session finishUploadWithResult:Answer(422, @"")];
  CHECK([TimesOf([session beginUploadAt:At(@"2026-11-23T09:02:00Z")]) isEqualToArray:@[@"2026-11-23T09:01:30Z"]]);
  CHECK(![session shouldStopAt:At(@"2026-11-23T13:59:59Z")]);
}

static void TestAcceptedUploadDropsBatch(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T09:00:20Z")];
  [session beginUploadAt:At(@"2026-11-23T09:01:00Z")];
  [session finishUploadWithResult:Answer(200, @"{}")];
  CHECK([session beginUploadAt:At(@"2026-11-23T09:03:00Z")].count == 0);
}

static void TestStopTimeCheck(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  CHECK(![session shouldStopAt:At(@"2026-11-23T13:59:59Z")]);
  CHECK([session shouldStopAt:At(@"2026-11-23T14:00:00Z")]);
  CHECK([session shouldStopAt:At(@"2026-11-23T14:00:01Z")]);
  CHECK([[[CrewTrackingSession alloc] initWithStartTime:[NSDate date] stopTime:nil] shouldStopAt:[NSDate date]]);
}

static CrewTrackingSession *SessionAfterAnswer(NSString *body) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session beginUploadAt:At(@"2026-11-23T09:02:00Z")];
  [session finishUploadWithResult:Answer(200, body)];
  return session;
}

static void TestAnswerMovesStopTime(void) {
  CrewTrackingSession *later = SessionAfterAnswer(@"{\"tracking_enabled\":true,\"tracking_until\":\"2026-11-23T16:00:00Z\"}");
  CHECK(![later shouldStopAt:At(@"2026-11-23T15:59:59Z")]);
  CHECK([later shouldStopAt:At(@"2026-11-23T16:00:00Z")]);

  CrewTrackingSession *earlier = SessionAfterAnswer(@"{\"tracking_enabled\":true,\"tracking_until\":\"2026-11-23T12:00:00Z\"}");
  CHECK(![earlier shouldStopAt:At(@"2026-11-23T11:59:59Z")]);
  CHECK([earlier shouldStopAt:At(@"2026-11-23T12:00:00Z")]);

  CHECK([SessionAfterAnswer(@"{\"tracking_enabled\":true,\"tracking_until\":null}") shouldStopAt:At(@"2026-11-23T09:02:01Z")]);
  CHECK([SessionAfterAnswer(@"{\"tracking_enabled\":false,\"tracking_until\":null}") shouldStopAt:At(@"2026-11-23T09:02:01Z")]);

  CrewTrackingSession *unreadable = SessionAfterAnswer(@"<html>oops</html>");
  CHECK(![unreadable shouldStopAt:At(@"2026-11-23T13:59:59Z")]);
  CHECK([unreadable shouldStopAt:At(@"2026-11-23T14:00:00Z")]);
}

static void TestFailedUploadKeepsStopTime(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session beginUploadAt:At(@"2026-11-23T09:02:00Z")];
  [session finishUploadWithResult:Answer(500, @"{\"tracking_enabled\":false,\"tracking_until\":null}")];
  CHECK(![session shouldStopAt:At(@"2026-11-23T13:59:59Z")]);
}

static void TestReadingsAtOrAfterStopTimeAreDropped(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T13:59:10Z")];
  [session addPoint:PointAt(@"2026-11-23T14:00:00Z")];
  [session addPoint:PointAt(@"2026-11-23T14:00:30Z")];
  CHECK([TimesOf([session takeRemainingPoints]) isEqualToArray:@[@"2026-11-23T13:59:10Z"]]);
  CHECK([session takeRemainingPoints].count == 0);
}

static void TestRemainingPointsFollowAnEarlierStopTime(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T11:58:10Z")];
  [session addPoint:PointAt(@"2026-11-23T12:00:10Z")];
  [session replaceStopTime:At(@"2026-11-23T12:00:00Z")];
  CHECK([TimesOf([session takeRemainingPoints]) isEqualToArray:@[@"2026-11-23T11:58:10Z"]]);
}

static void TestNoStopTimeDropsEveryPoint(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T09:00:10Z")];
  [session replaceStopTime:nil];
  [session addPoint:PointAt(@"2026-11-23T09:01:10Z")];
  CHECK([session takeRemainingPoints].count == 0);
}

static void TestSameMinuteReadingReplacesQueuedPoint(void) {
  CrewTrackingSession *session = SessionUntil(@"2026-11-23T14:00:00Z");
  [session addPoint:PointAt(@"2026-11-23T09:00:10Z")];
  [session addPoint:PointAt(@"2026-11-23T09:00:50Z")];
  CHECK([TimesOf([session beginUploadAt:At(@"2026-11-23T09:01:00Z")]) isEqualToArray:@[@"2026-11-23T09:00:50Z"]]);
}

int main(void) {
  @autoreleasepool {
    TestIso8601Parsing();
    TestStartOptions();
    TestQueueKeepsOnePointPerMinute();
    TestQueueHandsOverPointsOldestFirst();
    TestQueueRestoreKeepsNewerPoint();
    TestUploadOutcomes();
    TestAnswerParsing();
    TestEmptyUploadAfterTwoMinutes();
    TestPointsUploadOncePerTick();
    TestOneUploadInFlight();
    TestFailedUploadKeepsPoints();
    TestRejectedUploadDropsBatch();
    TestAcceptedUploadDropsBatch();
    TestStopTimeCheck();
    TestAnswerMovesStopTime();
    TestFailedUploadKeepsStopTime();
    TestReadingsAtOrAfterStopTimeAreDropped();
    TestRemainingPointsFollowAnEarlierStopTime();
    TestNoStopTimeDropsEveryPoint();
    TestSameMinuteReadingReplacesQueuedPoint();
    printf("%d checks, %d failed\n", checks, failedChecks);
  }
  return failedChecks == 0 ? 0 : 1;
}
