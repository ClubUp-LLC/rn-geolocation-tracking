#import "CrewTrackingTime.h"

static NSDateFormatter *CrewTrackingFormatter(NSString *format) {
  NSDateFormatter *formatter = [NSDateFormatter new];
  formatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
  formatter.calendar = [NSCalendar calendarWithIdentifier:NSCalendarIdentifierGregorian];
  formatter.dateFormat = format;
  return formatter;
}

static NSArray<NSDateFormatter *> *CrewTrackingFormatters(void) {
  static NSArray<NSDateFormatter *> *formatters;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    formatters = @[
      CrewTrackingFormatter(@"yyyy-MM-dd'T'HH:mm:ssXXXXX"),
      CrewTrackingFormatter(@"yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX"),
    ];
  });
  return formatters;
}

NSDate *CrewTrackingDateFromISO8601(id value) {
  if (![value isKindOfClass:[NSString class]]) {
    return nil;
  }
  for (NSDateFormatter *formatter in CrewTrackingFormatters()) {
    NSDate *date = [formatter dateFromString:value];
    if (date) {
      return date;
    }
  }
  return nil;
}
