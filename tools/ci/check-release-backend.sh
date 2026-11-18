#!/usr/bin/env bash
set -euo pipefail

# Install the matching release-targeted instrumentation APK, then exercise backend code on ART.
# Both APKs must use the same temporary CI signing identity; no production/private keystore is needed.
test_apk_root=${1:?Pass the extracted release AndroidTest APK directory}
log_root=startup-smoke-logs
mkdir -p "$log_root"
test_apk=$(find "$test_apk_root" -type f -name '*.apk' -print -quit)
if [[ -z "$test_apk" ]]; then
  echo "Missing release instrumentation APK" >&2
  exit 1
fi
adb install -r "$test_apk"
# Real on-device STT inference exercises the shipped JNI library and model format.
stt_fixture_root="${RUNNER_TEMP:-/tmp}/lastchat-stt-smoke"
python3 tools/ci/prepare-local-stt-smoke.py "$stt_fixture_root"
# The CI image is a rootable emulator; place fixtures in app-private storage with
# the app's ownership/SELinux label so the non-debuggable release reads them normally.
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]
adb root
adb wait-for-device
app_data_root=/data/user/0/lastchat.rikkafork.cocolal
app_fixture_root="$app_data_root/files/local-stt-smoke"
adb shell mkdir -p "$app_data_root/files"
adb shell rm -rf "$app_fixture_root"
adb push "$stt_fixture_root" "$app_fixture_root"
app_uid=$(adb shell stat -c %u "$app_data_root" | tr -d '\r')
adb shell chown -R "$app_uid:$app_uid" "$app_fixture_root"
adb shell restorecon -R "$app_fixture_root"
adb logcat -c
adb shell am instrument -w -r \
  -e class me.rerere.rikkahub.data.ai.PlaceholderRuntimeInstrumentedTest \
  lastchat.rikkafork.cocolal.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$log_root/stable-backend-instrumentation.txt"
adb logcat -d -b crash > "$log_root/stable-backend-crashes.txt"
if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log_root/stable-backend-instrumentation.txt"; then
  tail -n 80 "$log_root/stable-backend-crashes.txt" >&2
  echo "Stable release backend instrumentation failed" >&2
  exit 1
fi
if grep -Fq 'Process: lastchat.rikkafork.cocolal,' "$log_root/stable-backend-crashes.txt"; then
  echo "Stable release crashed during backend smoke tests" >&2
  exit 1
fi
