#import "NetworkService.h"

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

@end
