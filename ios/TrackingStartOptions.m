#import "TrackingStartOptions.h"
#import "CrewTrackingTime.h"

NSString * const TrackingUntilKey = @"trackingUntil";

@implementation TrackingStartOptions

- (instancetype)initWithParams:(NSDictionary *)params {
  self = [super init];
  if (self) {
    id trackingUntil = params[TrackingUntilKey];
    _crewMode = trackingUntil != nil;
    _stopTime = CrewTrackingDateFromISO8601(trackingUntil);
    NSMutableDictionary *headers = [params mutableCopy];
    [headers removeObjectForKey:TrackingUntilKey];
    _headers = [headers copy];
  }
  return self;
}

@end
