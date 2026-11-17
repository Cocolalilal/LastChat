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
adb logcat -c
adb shell am instrument -w -r \
  -e class me.rerere.rikkahub.data.ai.PlaceholderRuntimeInstrumentedTest \
  lastchat.rikkafork.cocolal.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$log_root/stable-backend-instrumentation.txt"
adb logcat -d -b crash > "$log_root/stable-backend-crashes.txt"
if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log_root/stable-backend-instrumentation.txt"; then
  echo "Stable release backend instrumentation failed" >&2
  exit 1
fi
if grep -Fq 'Process: lastchat.rikkafork.cocolal,' "$log_root/stable-backend-crashes.txt"; then
  echo "Stable release crashed during backend smoke tests" >&2
  exit 1
fi
