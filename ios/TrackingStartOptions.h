#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

FOUNDATION_EXPORT NSString * const TrackingUntilKey;

/// Splits the headers map from `startTracking` into HTTP headers and the crew stop time.
@interface TrackingStartOptions : NSObject

- (instancetype)initWithParams:(NSDictionary *)params;

/// The map without `trackingUntil`, ready to send as HTTP headers.
@property (nonatomic, readonly) NSDictionary *headers;
/// YES when the map carries `trackingUntil`, even as null; NO keeps 1.1.2 behaviour.
@property (nonatomic, readonly) BOOL crewMode;
/// The parsed stop time; nil when `trackingUntil` is null or unreadable.
@property (nonatomic, readonly, nullable) NSDate *stopTime;

@end

NS_ASSUME_NONNULL_END
