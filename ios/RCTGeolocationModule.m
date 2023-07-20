#import <CoreLocation/CoreLocation.h>
#import "RCTGeolocationModule.h"
#import "GeolocationService.h"

@implementation RCTGeolocationModule

RCT_EXPORT_MODULE(Geolocation);

RCT_EXPORT_METHOD(startTracking: (NSString *)url
                  parms:(NSDictionary *) dictionary
                  updatesIntervalSeconds: (int) seconds
                  distanceFilter: (int) meters
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
  
    dispatch_async(dispatch_get_main_queue(), ^(void){
             
        [[GeolocationService sharedManager] checkOrRequestPermissionsWithSuccess:^{
          [[GeolocationService sharedManager] configRequestManagerWithUrl: url
                                                                   params: dictionary
                                                           updatingPeriod: seconds
                                                           distanceFilter: meters];
          [[GeolocationService sharedManager] startTracking];
          resolve(@YES);
        } andFailure:^(NSString * _Nonnull reason, NSString * _Nonnull code) {
          reject(code, reason, nil);
        }];
    });
}

RCT_EXPORT_METHOD(stopTracking:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^(void){
      [[GeolocationService sharedManager] stopTracking];
      resolve(@YES);
  });
}

@end
