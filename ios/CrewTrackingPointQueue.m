#import "CrewTrackingPointQueue.h"

static const NSTimeInterval SecondsPerMinute = 60;

@implementation CrewTrackingPoint

- (instancetype)initWithTakenAt:(NSDate *)takenAt payload:(NSDictionary *)payload {
  self = [super init];
  if (self) {
    _takenAt = takenAt;
    _payload = [payload copy];
  }
  return self;
}

- (NSNumber *)minute {
  return @(floor(self.takenAt.timeIntervalSince1970 / SecondsPerMinute));
}

@end

@interface CrewTrackingPointQueue ()
@property (nonatomic, strong) NSMutableDictionary<NSNumber *, CrewTrackingPoint *> *pointsByMinute;
@end

@implementation CrewTrackingPointQueue

- (instancetype)init {
  self = [super init];
  if (self) {
    _pointsByMinute = [NSMutableDictionary new];
  }
  return self;
}

- (BOOL)isEmpty {
  return self.pointsByMinute.count == 0;
}

- (void)addPoint:(CrewTrackingPoint *)point {
  CrewTrackingPoint *queued = self.pointsByMinute[point.minute];
  if (queued && [queued.takenAt compare:point.takenAt] == NSOrderedDescending) {
    return;
  }
  self.pointsByMinute[point.minute] = point;
}

- (void)restorePoints:(NSArray<CrewTrackingPoint *> *)points {
  for (CrewTrackingPoint *point in points) {
    if (!self.pointsByMinute[point.minute]) {
      self.pointsByMinute[point.minute] = point;
    }
  }
}

- (NSArray<CrewTrackingPoint *> *)takeAllPoints {
  NSArray<NSNumber *> *minutes = [self.pointsByMinute.allKeys sortedArrayUsingSelector:@selector(compare:)];
  NSMutableArray<CrewTrackingPoint *> *points = [NSMutableArray arrayWithCapacity:minutes.count];
  for (NSNumber *minute in minutes) {
    [points addObject:self.pointsByMinute[minute]];
  }
  [self.pointsByMinute removeAllObjects];
  return points;
}

@end
