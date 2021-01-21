#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface NetworkService : NSObject

- (id) initWithUrl: (NSString *) url authParams: (NSDictionary*) params;
- (void) sendRequestWith: (nonnull id) params;

@end

NS_ASSUME_NONNULL_END
