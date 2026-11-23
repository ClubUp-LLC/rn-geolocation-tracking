#!/bin/sh
# Builds and runs the crew mode logic tests on macOS: ios/Tests/run.sh
set -e
IOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/crew-tracking-tests"
clang++ -fobjc-arc -Wall -Werror -framework Foundation -I "$IOS_DIR" \
  -x objective-c "$IOS_DIR"/CrewTracking*.m "$IOS_DIR"/TrackingStartOptions.m \
  -x objective-c++ "$IOS_DIR"/Tests/CrewTrackingTests.mm \
  -o "$OUT"
"$OUT"
