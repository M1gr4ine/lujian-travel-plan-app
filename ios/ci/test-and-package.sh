#!/usr/bin/env bash
set -euo pipefail

ios_root="$(cd "$(dirname "$0")/.." && pwd)"
build_root="$ios_root/build"
release_root="$build_root/release"
bundle_id="com.lujian.travelplan.ios"
version="1.0.0"

case "$build_root" in
  "$ios_root"/build) ;;
  *) echo "拒绝清理意外路径：$build_root" >&2; exit 1 ;;
esac

rm -rf "$build_root"
mkdir -p "$release_root"
cd "$ios_root"

xcodegen generate

udid="$(xcrun simctl list devices available -j | python3 -c 'import json,sys; d=json.load(sys.stdin)["devices"]; p=[x for g in d.values() for x in g if x.get("isAvailable") and x["name"].startswith("iPhone")]; preferred=next((x for x in p if x["name"].startswith("iPhone 16")), p[0]); print(preferred["udid"])')"
xcrun simctl boot "$udid" 2>/dev/null || true
xcrun simctl bootstatus "$udid" -b

xcodebuild test \
  -project Lujian.xcodeproj \
  -scheme Lujian \
  -destination "platform=iOS Simulator,id=$udid" \
  -resultBundlePath "$build_root/LujianTests.xcresult" \
  -parallel-testing-enabled NO \
  -maximum-concurrent-test-simulator-destinations 1

xcodebuild build \
  -project Lujian.xcodeproj \
  -scheme Lujian \
  -configuration Release \
  -destination "platform=iOS Simulator,id=$udid" \
  -derivedDataPath "$build_root/DerivedData-simulator"

simulator_app="$build_root/DerivedData-simulator/Build/Products/Release-iphonesimulator/Lujian.app"
test -d "$simulator_app"
xcrun simctl install "$udid" "$simulator_app"
xcrun simctl launch --terminate-running-process "$udid" "$bundle_id"
ditto -c -k --sequesterRsrc --keepParent \
  "$simulator_app" \
  "$release_root/Lujian-iOS-Simulator-$version.app.zip"

xcodebuild build \
  -project Lujian.xcodeproj \
  -scheme Lujian \
  -configuration Release \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$build_root/DerivedData-device" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY=""

device_app="$build_root/DerivedData-device/Build/Products/Release-iphoneos/Lujian.app"
test -d "$device_app"
payload_root="$build_root/unsigned/Payload"
mkdir -p "$payload_root"
ditto "$device_app" "$payload_root/Lujian.app"
ditto -c -k --sequesterRsrc \
  "$build_root/unsigned" \
  "$release_root/Lujian-iOS-Unsigned-$version.ipa"

cd "$release_root"
shasum -a 256 \
  "Lujian-iOS-Simulator-$version.app.zip" \
  "Lujian-iOS-Unsigned-$version.ipa" > SHA256SUMS.txt

test -s "Lujian-iOS-Simulator-$version.app.zip"
test -s "Lujian-iOS-Unsigned-$version.ipa"
test -s SHA256SUMS.txt
