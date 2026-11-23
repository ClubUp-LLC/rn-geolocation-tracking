#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, CrewUploadOutcome) {
  CrewUploadOutcomeAccepted, // 2xx
  CrewUploadOutcomeRejected, // 4xx: the batch is dropped
  CrewUploadOutcomeFailed,   // no network, timeout, 5xx: the batch waits for the next tick
};

typedef NS_ENUM(NSInteger, CrewAnswerKind) {
  CrewAnswerKindUnreadable, // changes nothing
  CrewAnswerKindStopNow,
  CrewAnswerKindStopAt,
};

/// What one crew upload came back with: its outcome and, for a 2xx, the tracking state.
@interface CrewTrackingUploadResult : NSObject

- (instancetype)initWithStatusCode:(NSInteger)statusCode
                              body:(nullable NSData *)body
                             error:(nullable NSError *)error;

@property (nonatomic, readonly) CrewUploadOutcome outcome;
/// Always CrewAnswerKindUnreadable unless the outcome is Accepted.
@property (nonatomic, readonly) CrewAnswerKind answerKind;
/// Set only for CrewAnswerKindStopAt.
@property (nonatomic, readonly, nullable) NSDate *stopTime;

@end

NS_ASSUME_NONNULL_END
