#!/bin/bash

# Clean Build Script for Surveillance Camera App
# This script clears all caches and builds fresh

echo "🧹 Starting complete clean build..."

# Stop any running gradle daemons
echo "Stopping Gradle daemons..."
./gradlew --stop

# Clean project
echo "Cleaning project..."
./gradlew clean

# Clear gradle caches
echo "Clearing Gradle caches..."
rm -rf ~/.gradle/caches/
rm -rf ~/.gradle/daemon/
rm -rf .gradle/

# Clear Android build caches
echo "Clearing Android build caches..."
rm -rf app/build/
rm -rf build/

# Clear any remaining cache directories
echo "Clearing additional cache directories..."
find . -name ".gradle" -type d -exec rm -rf {} + 2>/dev/null || true
find . -name "build" -type d -exec rm -rf {} + 2>/dev/null || true

# Build fresh
echo "🔨 Building fresh APK (no cache)..."
./gradlew assembleDebug --no-build-cache --no-configuration-cache --no-daemon --rerun-tasks

echo "✅ Clean build complete!"
echo "📱 Install with: adb install app/build/outputs/apk/debug/app-debug.apk"
