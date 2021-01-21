#import <CoreLocation/CoreLocation.h>

#import "GeolocationService.h"
#import "NetworkService.h"

@interface GeolocationService() <CLLocationManagerDelegate>

@property (nonatomic, strong) CLLocationManager *manager;
@property (nonatomic) AccessLocationAcceptedBlock accesBlock;
@property (nonatomic) AccessLocationRejectedBlock rejectBlock;
@property (nonatomic, strong) NSMutableArray<CLLocation*> *locationArray;
@property (nonatomic, strong) NetworkService *networkService;
@property (nonatomic) int period;
@property (nonatomic, strong) NSDateFormatter *iso8601Formatter;

@end

@implementation GeolocationService

static GeolocationService *_sharedManager = nil;

+ (GeolocationService *)sharedManager {
  
    if (!_sharedManager) {
        @synchronized(self) {
            if (!_sharedManager)
              _sharedManager = [GeolocationService new];
        }
    }
  
    return _sharedManager;
}

- (instancetype)init {
  
    self = [super init];
    if (self) {
        _isTracking = NO;
        _locationArray = @[].mutableCopy;
        _manager = [CLLocationManager new];
        _manager.delegate = self;
        _manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation;
        [_manager setAllowsBackgroundLocationUpdates: YES];
        [_manager setActivityType:CLActivityTypeFitness];
      
        _iso8601Formatter = [NSDateFormatter new];
        [_iso8601Formatter setLocale: [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"]];
        [_iso8601Formatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ssZZZZZ"];
        [_iso8601Formatter setCalendar: [NSCalendar calendarWithIdentifier:NSCalendarIdentifierGregorian]];

    }
    
    return self;
}

- (void)checkOrRequestPermissionsWithSuccess:(AccessLocationAcceptedBlock)succses andFailure:(AccessLocationRejectedBlock)failure {
    _accesBlock = succses;
    _rejectBlock = failure;
    [self validatePermissionWithStatus:[CLLocationManager authorizationStatus]];
}


- (void)configRequestManagerWithUrl:(NSString *)url
                             params:(NSDictionary *)params
                     updatingPeriod:(int)period
                     distanceFilter:(int)meters {

  _manager.distanceFilter = meters;
  _networkService = [[NetworkService alloc] initWithUrl:url authParams:params];
}

- (void)startTracking {
  if (_isTracking)
    return;
  _isTracking = YES;
  [_manager startUpdatingLocation];
}

- (void)stopTracking {
  _isTracking = NO;
  [_manager stopUpdatingLocation];
}

- (void) validatePermissionWithStatus: (CLAuthorizationStatus) status {
  
  if ((_accesBlock == nil) && (_rejectBlock == nil)) {
      return;
  }
  
  switch (status) {
    case kCLAuthorizationStatusNotDetermined:
      [_manager requestWhenInUseAuthorization];
      break;
    case kCLAuthorizationStatusRestricted:
      _rejectBlock(@"Access to geolocation is limited");
      break;
    case kCLAuthorizationStatusDenied:
      _rejectBlock(@"Access to geolocation is denied");
      break;
    case kCLAuthorizationStatusAuthorizedAlways:
      _accesBlock();
      break;
    case kCLAuthorizationStatusAuthorizedWhenInUse:
      _accesBlock();
      break;
    default:
      _rejectBlock(@"Unknown error");
      break;
  }
  
  if (status != kCLAuthorizationStatusNotDetermined) {
      _accesBlock = nil;
      _rejectBlock = nil;
  }
}

#pragma mark -CLLocationManagerDelegate

- (void)locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
  [self validatePermissionWithStatus:[CLLocationManager authorizationStatus]];
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray<CLLocation *> *)locations {
  
  NSMutableArray<NSDictionary *> *locationArray = @[].mutableCopy;
  
  for (CLLocation *location in locations) {
    NSMutableDictionary *dictionary = @{}.mutableCopy;
    [dictionary setValue: [NSString stringWithFormat:@"%@", [_iso8601Formatter stringFromDate: location.timestamp]] forKey:@"time"];
    [dictionary setValue: [NSString stringWithFormat:@"%f", location.coordinate.latitude] forKey:@"latitude"];
    [dictionary setValue: [NSString stringWithFormat:@"%f", location.coordinate.longitude] forKey:@"longitude"];
    [dictionary setValue:[NSString stringWithFormat:@"%f", location.horizontalAccuracy] forKey:@"accuracy"];
    [dictionary setValue:[NSString stringWithFormat:@"%f", location.altitude] forKey:@"altitude"];
    [locationArray addObject:dictionary];
  }
  
  [_networkService sendRequestWith: @{@"points" : locationArray}];
}

@end
