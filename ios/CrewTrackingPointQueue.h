#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// One location reading: when the phone took it and the JSON object the server receives.
@interface CrewTrackingPoint : NSObject

- (instancetype)initWithTakenAt:(NSDate *)takenAt payload:(NSDictionary *)payload;

@property (nonatomic, readonly) NSDate *takenAt;
@property (nonatomic, readonly) NSDictionary *payload;

@end

/// Points waiting for upload, at most one per clock minute, oldest first.
@interface CrewTrackingPointQueue : NSObject

@property (nonatomic, readonly) BOOL isEmpty;

/// A newer reading replaces the queued point of the same minute.
- (void)addPoint:(CrewTrackingPoint *)point;
/// Puts back the points of a failed upload; a point queued meanwhile for the same minute wins.
- (void)restorePoints:(NSArray<CrewTrackingPoint *> *)points;
- (NSArray<CrewTrackingPoint *> *)takeAllPoints;

@end

NS_ASSUME_NONNULL_END
