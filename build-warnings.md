# Build Warnings Log

**Last Updated:** August 17, 2025

## Current Build Warnings (Non-Critical)

### Java Native Access Warnings
```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by net.rubygrapefruit.platform.internal.NativeLibraryLoader in an unnamed module (file:/Users/h17/AndroidStudioProjects/Surveillancecamera/gradle/wrapper/dists/gradle-8.14-bin/38aieal9i53h9rfe7vjup95b9/gradle-8.14/lib/native-platform-0.22-milestone-28.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

**Status:** Already addressed in gradle.properties with `--enable-native-access=ALL-UNNAMED` flag
**Impact:** Warnings persist but functionality works. Future Gradle versions may require additional configuration.

### ML Kit Native Libraries
```
> Task :app:stripDebugDebugSymbols
Unable to strip the following libraries, packaging them as they are: libface_detector_v2_jni.so, libimage_processing_util_jni.so, libmlkit_google_ocr_pipeline.so, libmlkitcommonpipeline.so.
```

**Status:** Expected behavior - Google ML Kit native libraries cannot be stripped for optimization
**Impact:** Slightly larger APK size but no functionality issues

### Kotlin Compilation Warnings
```
w: file:///Users/h17/AndroidStudioProjects/Surveillancecamera/app/src/main/java/com/hpalma/Surveillance247/MainActivity.kt:134:13 Variable 'context' is never used
w: file:///Users/h17/AndroidStudioProjects/Surveillancecamera/app/src/main/java/com/hpalma/Surveillance247/StreamingServer.kt:8:23 Parameter 'port' is never used  
w: file:///Users/h17/AndroidStudioProjects/Surveillancecamera/app/src/main/java/com/hpalma/Surveillance247/StreamingServer.kt:11:21 Parameter 'frameData' is never used
```

**Status:** Minor code cleanup needed
**Impact:** No functional issues, just unused variables/parameters

## Build Success Status ✅
- **Build Result:** SUCCESS
- **APK Generated:** `app/build/outputs/apk/debug/app-debug.apk`
- **Configuration Cache:** Reused successfully
- **ML Kit Integration:** Working (native libraries packaged correctly)

## Action Items for Future Cleanup
- [ ] Remove unused `context` variable from MainActivity.kt line 134
- [ ] Clean up StreamingServer.kt unused parameters (port, frameData) - currently disabled file
- [ ] Monitor Gradle native access warnings for future versions

## Notes
All warnings are non-critical and do not affect app functionality. The app builds successfully with full ML Kit integration and split-screen surveillance UI.
