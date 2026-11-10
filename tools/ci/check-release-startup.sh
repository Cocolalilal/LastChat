#!/usr/bin/env bash
set -euo pipefail

artifact_root=${1:?Pass the extracted release APK directory}
log_root=startup-smoke-logs
mkdir -p "$log_root"

for flavor in stable dev; do
  app_id=lastchat.rikkafork.cocolal
  if [[ "$flavor" == dev ]]; then app_id+=.dev; fi
  apk=$(find "$artifact_root" -type f -name "*_${flavor}-x86_64-release.apk" -print -quit)
  if [[ -z "$apk" ]]; then
    echo "Missing x86_64 release APK for $flavor" >&2
    exit 1
  fi
  adb install -r "$apk"
  adb shell am force-stop "$app_id"
  adb logcat -c
  adb shell am start -W -n "$app_id/me.rerere.rikkahub.RouteActivity" | tee "$log_root/$flavor-launch.txt"

  for attempt in 1 2 3; do
    sleep 5
    if ! adb shell pidof "$app_id" > "$log_root/$flavor-pid.txt"; then
      adb logcat -d > "$log_root/$flavor-logcat.txt"
      cat "$log_root/$flavor-logcat.txt"
      echo "$flavor release died during startup" >&2
      exit 1
    fi
  done
  adb logcat -d -b crash > "$log_root/$flavor-crashes.txt"
  if grep -Fq "Process: $app_id," "$log_root/$flavor-crashes.txt"; then
    cat "$log_root/$flavor-crashes.txt"
    exit 1
  fi
  adb shell dumpsys activity activities > "$log_root/$flavor-activities.txt"
  adb exec-out screencap -p > "$log_root/$flavor-screen.png"
  echo "$flavor release stayed alive through startup"
  adb shell am force-stop "$app_id"
done
