#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^AccessLocationAcceptedBlock)(void);
typedef void (^AccessLocationRejectedBlock)(NSString *reason);

@interface GeolocationService : NSObject

+ (GeolocationService *)sharedManager;

@property (nonatomic) BOOL isTracking;

- (void) configRequestManagerWithUrl: (NSString *) url
                              params: (NSDictionary *) params
                      updatingPeriod: (int) period
                      distanceFilter: (int) meters;

- (void) checkOrRequestPermissionsWithSuccess: (AccessLocationAcceptedBlock) succses
                                   andFailure: (AccessLocationRejectedBlock) failure;
- (void) startTracking;
- (void) stopTracking;

@end

NS_ASSUME_NONNULL_END
