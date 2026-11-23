#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Reads an ISO 8601 time such as `2026-11-23T19:00:00Z`; anything else gives nil.
FOUNDATION_EXPORT NSDate * _Nullable CrewTrackingDateFromISO8601(id _Nullable value);

NS_ASSUME_NONNULL_END
