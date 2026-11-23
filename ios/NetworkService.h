#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^PointsUploadCompletion)(NSInteger statusCode, NSData * _Nullable body, NSError * _Nullable error);

@interface NetworkService : NSObject

- (id) initWithUrl: (NSString *) url failureUrl: (NSString *) failureUrl authParams: (NSDictionary*) params;
- (void) sendRequestWith: (nonnull id) params failure:(BOOL) failure;
/// Crew mode upload: gives up after 30 seconds and hands the answer back.
- (void) uploadPoints: (NSArray<NSDictionary *> *) points completion: (PointsUploadCompletion) completion;

@end

NS_ASSUME_NONNULL_END
