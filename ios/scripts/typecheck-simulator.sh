#!/bin/bash
# Type-check the iOS app sources against the iOS Simulator SDK, without xcodebuild.
#
# WHY THIS EXISTS
#
# On this machine `xcodebuild` reports zero iOS destinations — device and simulator alike —
# because Xcode's iOS 26.5 platform support component is not installed:
#
#   { platform:iOS, name:Any iOS Device, error:iOS 26.5 is not installed.
#     Please download and install the platform from Xcode > Settings > Components. }
#
# The simulator *runtimes* (iOS 18.6 and 26.3) are installed and `simctl` can boot them, and the
# iPhoneSimulator26.5 SDK is on disk — but xcodebuild will not pair them, so `-destination`,
# `-sdk` and `generic/platform=iOS Simulator` all fail before compiling a single file. Fix it
# with `xcodebuild -downloadPlatform iOS` (a multi-gigabyte download) or Xcode > Settings >
# Components.
#
# Until then this script gives the one guarantee that does not need a destination: that the app's
# Swift actually compiles against the real iOS SDK, with the real SwiftUI and SwiftData. It is a
# stopgap for a broken toolchain, not a substitute for building and running the app — it proves
# nothing about layout, behaviour, or anything at runtime.
#
#   ./ios/scripts/typecheck-simulator.sh

set -euo pipefail

cd "$(dirname "$0")/.."

SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
TARGET="arm64-apple-ios17.0-simulator"
BUILD="${TMPDIR:-/tmp}/aynama-typecheck"
ADHAN="SharedLogic/.build/checkouts/adhan-swift/Sources"

if [ ! -d "$ADHAN" ]; then
  echo "resolving packages..."
  (cd SharedLogic && swift build >/dev/null)
fi

rm -rf "$BUILD"
mkdir -p "$BUILD"

common=(-sdk "$SDK" -target "$TARGET" -swift-version 6)

echo "[1/3] Adhan"
swiftc "${common[@]}" \
  -emit-module -module-name Adhan \
  -emit-module-path "$BUILD/Adhan.swiftmodule" \
  $(find "$ADHAN" -name '*.swift')

echo "[2/3] SharedLogic"
swiftc "${common[@]}" -I "$BUILD" \
  -emit-module -module-name SharedLogic \
  -emit-module-path "$BUILD/SharedLogic.swiftmodule" \
  $(find SharedLogic/Sources -name '*.swift')

echo "[3/3] Aynama app"
swiftc "${common[@]}" -I "$BUILD" \
  -typecheck -module-name Aynama \
  $(find App -name '*.swift')

echo "OK — the app type-checks against $(basename "$SDK") for $TARGET"
