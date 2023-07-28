#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface NetworkService : NSObject

- (id) initWithUrl: (NSString *) url failureUrl: (NSString *) failureUrl authParams: (NSDictionary*) params;
- (void) sendRequestWith: (nonnull id) params failure:(BOOL) failure;

@end

NS_ASSUME_NONNULL_END
