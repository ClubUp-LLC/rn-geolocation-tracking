#import <Foundation/Foundation.h>

#import "CrewTrackingPointQueue.h"
#import "CrewTrackingUploadResult.h"

NS_ASSUME_NONNULL_BEGIN

FOUNDATION_EXPORT const NSTimeInterval CrewTrackingTickInterval;

/// Crew mode state with no system calls: queued points, upload timing and the stop time.
@interface CrewTrackingSession : NSObject

- (instancetype)initWithStartTime:(NSDate *)startTime stopTime:(nullable NSDate *)stopTime;

/// A later or earlier time replaces the stop time; nil means "stop now".
- (void)replaceStopTime:(nullable NSDate *)stopTime;
- (BOOL)shouldStopAt:(NSDate *)now;

- (void)addPoint:(CrewTrackingPoint *)point;

- (BOOL)isUploadDueAt:(NSDate *)now;
/// Marks an upload in flight and hands over the queued points; may be empty.
- (NSArray<CrewTrackingPoint *> *)beginUploadAt:(NSDate *)now;
- (void)finishUploadWithResult:(CrewTrackingUploadResult *)result;

/// Empties the queue for the last upload, without points taken at or after the stop time.
- (NSArray<CrewTrackingPoint *> *)takeRemainingPoints;

@end

NS_ASSUME_NONNULL_END
