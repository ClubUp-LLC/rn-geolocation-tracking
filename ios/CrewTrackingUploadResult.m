#import "CrewTrackingUploadResult.h"
#import "CrewTrackingTime.h"

@implementation CrewTrackingUploadResult

- (instancetype)initWithStatusCode:(NSInteger)statusCode body:(NSData *)body error:(NSError *)error {
  self = [super init];
  if (self) {
    _outcome = [self.class outcomeForStatusCode:statusCode error:error];
    _answerKind = CrewAnswerKindUnreadable;
    if (_outcome == CrewUploadOutcomeAccepted) {
      [self readAnswer:body];
    }
  }
  return self;
}

+ (CrewUploadOutcome)outcomeForStatusCode:(NSInteger)statusCode error:(NSError *)error {
  if (error == nil && statusCode >= 200 && statusCode < 300) {
    return CrewUploadOutcomeAccepted;
  }
  if (error == nil && statusCode >= 400 && statusCode < 500) {
    return CrewUploadOutcomeRejected;
  }
  return CrewUploadOutcomeFailed;
}

- (void)readAnswer:(NSData *)body {
  NSDictionary *answer = [self.class jsonObjectFrom:body];
  id enabled = answer[@"tracking_enabled"];
  id until = answer[@"tracking_until"];

  if ([enabled isKindOfClass:[NSNumber class]] && ![enabled boolValue]) {
    _answerKind = CrewAnswerKindStopNow;
  } else if (until == [NSNull null]) {
    _answerKind = CrewAnswerKindStopNow;
  } else {
    _stopTime = CrewTrackingDateFromISO8601(until);
    _answerKind = _stopTime ? CrewAnswerKindStopAt : CrewAnswerKindUnreadable;
  }
}

+ (nullable NSDictionary *)jsonObjectFrom:(NSData *)body {
  if (body.length == 0) {
    return nil;
  }
  id json = [NSJSONSerialization JSONObjectWithData:body options:0 error:nil];
  return [json isKindOfClass:[NSDictionary class]] ? json : nil;
}

@end
