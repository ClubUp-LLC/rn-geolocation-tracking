#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>

#import "CrewTrackingSession.h"
#import "GeolocationService.h"
#import "NetworkService.h"
#import "TrackingStartOptions.h"

static const CLLocationDistance CrewDistanceFilterMeters = 50;
static const NSTimeInterval CrewTickLeeway = 5;

@interface GeolocationService() <CLLocationManagerDelegate>

@property (nonatomic, strong) CLLocationManager *manager;
@property (nonatomic) AccessLocationAcceptedBlock accesBlock;
@property (nonatomic) AccessLocationRejectedBlock rejectBlock;
@property (nonatomic, strong) NSMutableArray<CLLocation*> *locationArray;
@property (nonatomic, strong) NetworkService *networkService;
@property (nonatomic) int period;
@property (nonatomic, strong) NSDateFormatter *iso8601Formatter;
/// Set only while the app tracks in crew mode, that is with a `trackingUntil` key.
@property (nonatomic, strong, nullable) CrewTrackingSession *crewSession;
@property (nonatomic, readonly) BOOL crewMode;
@property (nonatomic, strong, nullable) dispatch_source_t crewTickTimer;

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
        _manager.desiredAccuracy = kCLLocationAccuracyBest;
        [_manager setAllowsBackgroundLocationUpdates: YES];
        [_manager setShowsBackgroundLocationIndicator: YES];
        [_manager setActivityType:CLActivityTypeFitness];
        [_manager setPausesLocationUpdatesAutomatically: NO];

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
                         failureUrl:(NSString *)failureUrl
                             params:(NSDictionary *)params
                     updatingPeriod:(int)period
                     distanceFilter:(int)meters {

  TrackingStartOptions *options = [[TrackingStartOptions alloc] initWithParams:params];
  _networkService = [[NetworkService alloc] initWithUrl:url failureUrl: failureUrl authParams:options.headers];
  if (options.crewMode) {
    [self applyCrewModeWithStopTime:options.stopTime];
  } else {
    [self applyLegacyModeWithDistanceFilter:meters];
  }
}

- (void)startTracking {
  if (self.crewMode) {
    [self startCrewTracking];
  } else {
    [self startLocationUpdates];
  }
}

- (void)stopTracking {
  [self endCrewSession];
  _isTracking = NO;
  [_manager stopUpdatingLocation];
}

- (BOOL)crewMode {
  return self.crewSession != nil;
}

- (void)startLocationUpdates {
  if (_isTracking)
    return;
  _isTracking = YES;
  [_manager startUpdatingLocation];
}

#pragma mark - 1.1.2 mode

- (void)applyLegacyModeWithDistanceFilter:(int)meters {
  [self endCrewSession];
  _manager.desiredAccuracy = kCLLocationAccuracyBest;
  _manager.distanceFilter = meters;
}

- (void)sendReadingsNow:(NSArray<CLLocation *> *)locations {
  NSMutableArray<NSDictionary *> *locationArray = @[].mutableCopy;
  for (CLLocation *location in locations) {
    [locationArray addObject:[self payloadForLocation:location]];
  }
  [_networkService sendRequestWith: @{@"points" : locationArray} failure: NO];
}

#pragma mark - Crew mode

- (void)applyCrewModeWithStopTime:(nullable NSDate *)stopTime {
  if (self.crewSession) {
    [self.crewSession replaceStopTime:stopTime];
  } else {
    self.crewSession = [[CrewTrackingSession alloc] initWithStartTime:[NSDate date] stopTime:stopTime];
  }
  _manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters;
  _manager.distanceFilter = CrewDistanceFilterMeters;
}

- (void)startCrewTracking {
  if ([self.crewSession shouldStopAt:[NSDate date]]) {
    [self stopTracking];
    return;
  }
  [self startCrewTick];
  [self startLocationUpdates];
}

- (void)endCrewSession {
  if (!self.crewSession)
    return;
  [self stopCrewTick];
  [self uploadLastPoints:[self.crewSession takeRemainingPoints]];
  self.crewSession = nil;
}

- (void)queueReadings:(NSArray<CLLocation *> *)locations {
  for (CLLocation *location in locations) {
    [self.crewSession addPoint:[[CrewTrackingPoint alloc] initWithTakenAt:location.timestamp
                                                                   payload:[self payloadForLocation:location]]];
  }
  if ([self.crewSession shouldStopAt:[NSDate date]]) {
    [self stopTracking];
  }
}

- (void)startCrewTick {
  if (self.crewTickTimer)
    return;
  __weak GeolocationService *weakSelf = self;
  dispatch_source_t timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, dispatch_get_main_queue());
  // Wall clock time, so the stop time check stays right after the phone sleeps.
  dispatch_source_set_timer(timer,
                            dispatch_walltime(NULL, (int64_t)(CrewTrackingTickInterval * NSEC_PER_SEC)),
                            (uint64_t)(CrewTrackingTickInterval * NSEC_PER_SEC),
                            (uint64_t)(CrewTickLeeway * NSEC_PER_SEC));
  dispatch_source_set_event_handler(timer, ^{
    [weakSelf crewTick];
  });
  dispatch_resume(timer);
  self.crewTickTimer = timer;
}

- (void)stopCrewTick {
  if (!self.crewTickTimer)
    return;
  dispatch_source_cancel(self.crewTickTimer);
  self.crewTickTimer = nil;
}

- (void)crewTick {
  NSDate *now = [NSDate date];
  CrewTrackingSession *session = self.crewSession;
  if ([session shouldStopAt:now]) {
    [self stopTracking];
  } else if ([session isUploadDueAt:now]) {
    [self uploadPoints:[session beginUploadAt:now] ofSession:session];
  }
}

- (void)uploadPoints:(NSArray<CrewTrackingPoint *> *)points ofSession:(CrewTrackingSession *)session {
  __weak GeolocationService *weakSelf = self;
  [_networkService uploadPoints:[points valueForKey:@"payload"] completion:^(NSInteger statusCode, NSData *body, NSError *error) {
    CrewTrackingUploadResult *result = [[CrewTrackingUploadResult alloc] initWithStatusCode:statusCode body:body error:error];
    dispatch_async(dispatch_get_main_queue(), ^{
      [weakSelf finishUploadOfSession:session withResult:result];
    });
  }];
}

- (void)finishUploadOfSession:(CrewTrackingSession *)session withResult:(CrewTrackingUploadResult *)result {
  if (session != self.crewSession)
    return;
  [session finishUploadWithResult:result];
  if ([session shouldStopAt:[NSDate date]]) {
    [self stopTracking];
  }
}

- (void)uploadLastPoints:(NSArray<CrewTrackingPoint *> *)points {
  if (points.count == 0)
    return;
  // Location updates stop right after this, so a background task keeps the app awake for the upload.
  UIApplication *application = [UIApplication sharedApplication];
  __block UIBackgroundTaskIdentifier task = UIBackgroundTaskInvalid;
  void (^endTask)(void) = ^{
    if (task == UIBackgroundTaskInvalid)
      return;
    [application endBackgroundTask:task];
    task = UIBackgroundTaskInvalid;
  };
  task = [application beginBackgroundTaskWithExpirationHandler:endTask];
  [_networkService uploadPoints:[points valueForKey:@"payload"] completion:^(NSInteger statusCode, NSData *body, NSError *error) {
    dispatch_async(dispatch_get_main_queue(), endTask);
  }];
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
      _rejectBlock(@"Access to geolocation is limited", @"missing_permission");
      break;
    case kCLAuthorizationStatusDenied:
      _rejectBlock(@"Access to geolocation is denied", @"missing_permission");
      break;
    case kCLAuthorizationStatusAuthorizedAlways:
      _accesBlock();
      break;
    case kCLAuthorizationStatusAuthorizedWhenInUse:
      _accesBlock();
      break;
    default:
      _rejectBlock(@"Unknown error", @"unknown");
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
  if (self.crewMode) {
    [self queueReadings:locations];
  } else {
    [self sendReadingsNow:locations];
  }
}

- (NSDictionary *)payloadForLocation:(CLLocation *)location {
  NSMutableDictionary *dictionary = @{}.mutableCopy;
  [dictionary setValue: [NSString stringWithFormat:@"%@", [_iso8601Formatter stringFromDate: location.timestamp]] forKey:@"time"];
  [dictionary setValue: [NSString stringWithFormat:@"%f", location.coordinate.latitude] forKey:@"latitude"];
  [dictionary setValue: [NSString stringWithFormat:@"%f", location.coordinate.longitude] forKey:@"longitude"];
  [dictionary setValue: [NSString stringWithFormat:@"%f", location.horizontalAccuracy] forKey:@"accuracy"];
  [dictionary setValue: [NSString stringWithFormat:@"%f", location.altitude] forKey:@"altitude"];
  return dictionary;
}

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error {

    [_networkService sendRequestWith: @{ @"message": error.description} failure: YES];
}

@end
