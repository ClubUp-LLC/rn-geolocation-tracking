#import "CrewTrackingSession.h"

const NSTimeInterval CrewTrackingTickInterval = 60;
// The timer fires a little late at times, so each gap is measured with this slack.
static const NSTimeInterval TickSlack = 5;
static const NSTimeInterval PointsUploadGap = CrewTrackingTickInterval - TickSlack;
static const NSTimeInterval EmptyUploadGap = 2 * CrewTrackingTickInterval - TickSlack;

@interface CrewTrackingSession ()
@property (nonatomic, strong) CrewTrackingPointQueue *queue;
@property (nonatomic, strong, nullable) NSDate *stopTime;
@property (nonatomic, strong) NSDate *lastRequestAt;
@property (nonatomic, copy, nullable) NSArray<CrewTrackingPoint *> *inFlightPoints;
@end

@implementation CrewTrackingSession

- (instancetype)initWithStartTime:(NSDate *)startTime stopTime:(NSDate *)stopTime {
  self = [super init];
  if (self) {
    _queue = [CrewTrackingPointQueue new];
    _lastRequestAt = startTime;
    _stopTime = stopTime;
  }
  return self;
}

- (void)replaceStopTime:(NSDate *)stopTime {
  self.stopTime = stopTime;
}

- (BOOL)shouldStopAt:(NSDate *)now {
  return [self isAtOrAfterStopTime:now];
}

- (void)addPoint:(CrewTrackingPoint *)point {
  [self.queue addPoint:point];
}

- (BOOL)isUploadDueAt:(NSDate *)now {
  if (self.inFlightPoints) {
    return NO;
  }
  NSTimeInterval sinceLastRequest = [now timeIntervalSinceDate:self.lastRequestAt];
  NSTimeInterval gap = self.queue.isEmpty ? EmptyUploadGap : PointsUploadGap;
  return sinceLastRequest >= gap;
}

- (NSArray<CrewTrackingPoint *> *)beginUploadAt:(NSDate *)now {
  self.lastRequestAt = now;
  self.inFlightPoints = [self.queue takeAllPoints];
  return self.inFlightPoints;
}

- (void)finishUploadWithResult:(CrewTrackingUploadResult *)result {
  switch (result.outcome) {
    case CrewUploadOutcomeAccepted:
      [self applyAnswerOf:result];
      break;
    case CrewUploadOutcomeRejected:
      break;
    case CrewUploadOutcomeFailed:
      [self.queue restorePoints:self.inFlightPoints];
      break;
  }
  self.inFlightPoints = nil;
}

- (NSArray<CrewTrackingPoint *> *)takeRemainingPoints {
  NSArray<CrewTrackingPoint *> *points = [self.queue takeAllPoints];
  NSIndexSet *beforeStop = [points indexesOfObjectsPassingTest:^BOOL(CrewTrackingPoint *point, NSUInteger index, BOOL *stop) {
    return ![self isAtOrAfterStopTime:point.takenAt];
  }];
  return [points objectsAtIndexes:beforeStop];
}

#pragma mark - Private

- (void)applyAnswerOf:(CrewTrackingUploadResult *)result {
  switch (result.answerKind) {
    case CrewAnswerKindStopAt:
      self.stopTime = result.stopTime;
      break;
    case CrewAnswerKindStopNow:
      self.stopTime = nil;
      break;
    case CrewAnswerKindUnreadable:
      break;
  }
}

- (BOOL)isAtOrAfterStopTime:(NSDate *)date {
  return self.stopTime == nil || [date compare:self.stopTime] != NSOrderedAscending;
}

@end
