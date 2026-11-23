#import "NetworkService.h"

static const NSTimeInterval PointsUploadTimeout = 30;

@interface NetworkService ()
@property (nonnull, nonatomic, strong) NSString* url;
@property (nonnull, nonatomic, strong) NSString* failureUrl;
@property (nonnull, nonatomic, strong) NSDictionary<NSString*, NSString*>* headers;
@property (nonnull, nonatomic, strong) NSString* method;
@property (nonatomic) int timeoutInterval;
@end

@implementation NetworkService

- (id)initWithUrl:(NSString *)url failureUrl: (NSString *)failureUrl authParams:(nonnull NSDictionary *)params {
  self = [super init];
  if (self) {
    _url = url;
    _failureUrl = failureUrl;
    NSMutableDictionary* paramsDictionary = [[NSMutableDictionary alloc] initWithDictionary: params];
    [paramsDictionary setValue:@"application/json; charset=utf-8" forKey:@"Content-Type"];
    _headers = [[NSDictionary alloc] initWithDictionary: paramsDictionary];
    _timeoutInterval = 15000;
    
  }
  return self;
}

- (void)sendRequestWith:(nonnull id)params failure:(BOOL)failure {
    if (failure && _failureUrl == nil) {
        return;
    }
    NSMutableURLRequest *request = [[NSMutableURLRequest alloc] initWithURL:[NSURL URLWithString:failure ? _failureUrl : _url]];
  
  if (request == nil) {
    return;
  }

  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:params options:NSJSONWritingPrettyPrinted error:nil];
  request.HTTPMethod = @"POST";
  request.timeoutInterval = _timeoutInterval;
  [request setAllHTTPHeaderFields: _headers];
  request.HTTPBody = jsonData;

  NSURLSession *session = [NSURLSession sharedSession];

  [[session dataTaskWithRequest: request completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
      NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *) response;
    NSLog(@"HTTP Status: %ld", (long)httpResponse.statusCode);
    
  }] resume];
  
}

- (void)uploadPoints:(NSArray<NSDictionary *> *)points completion:(PointsUploadCompletion)completion {
  NSURL *url = [NSURL URLWithString:_url];
  if (url == nil) {
    completion(0, nil, [NSError errorWithDomain:NSURLErrorDomain code:NSURLErrorBadURL userInfo:nil]);
    return;
  }
  NSMutableURLRequest *request = [[NSMutableURLRequest alloc] initWithURL:url];
  request.HTTPMethod = @"POST";
  [request setAllHTTPHeaderFields: _headers];
  request.HTTPBody = [NSJSONSerialization dataWithJSONObject:@{@"points": points} options:0 error:nil];

  [[[self.class pointsUploadSession] dataTaskWithRequest:request completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
    NSInteger statusCode = [response isKindOfClass:[NSHTTPURLResponse class]] ? ((NSHTTPURLResponse *) response).statusCode : 0;
    NSLog(@"HTTP Status: %ld", (long)statusCode);
    completion(statusCode, data, error);
  }] resume];
}

+ (NSURLSession *)pointsUploadSession {
  static NSURLSession *session;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
    configuration.timeoutIntervalForRequest = PointsUploadTimeout;
    configuration.timeoutIntervalForResource = PointsUploadTimeout;
    session = [NSURLSession sessionWithConfiguration:configuration];
  });
  return session;
}

@end
