#!/usr/bin/env bash

set -euo pipefail

echo "VASU Phase 4 verification started"

./gradlew test
./gradlew assembleDebug
./gradlew lint

APK_COUNT="$(find app/build/outputs/apk/debug -name '*.apk' | wc -l | tr -d ' ')"

if [ "$APK_COUNT" -lt 1 ]; then
  echo "ERROR: debug APK was not produced"
  exit 1
fi

echo "VASU Phase 4 verification passed"
echo "Debug APK count: $APK_COUNT"
