#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="$HOME/.local/share/jdks/temurin-17/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_HOME="$HOME/.local/share/gradle/gradle-8.10.2"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$GRADLE_HOME/bin:$PATH"

gradle "$@"
