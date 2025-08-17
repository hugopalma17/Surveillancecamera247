# Surveillance Camera App - Development Roadmap

## Project Overview
**Goal:** Create a power-efficient security camera app that runs locally on Android devices.

**Core Features:**
- Record video when both movement and object detection occur
- Save faces and license plates as timestamped images
- 100GB circular storage management
- Continuous background operation via Foreground Service

## Completed Phases ✅

### Phase 1: Core Service Implementation ✅ COMPLETED
**Status:** Fully functional basic mode
- ✅ Foreground Service with proper notification
- ✅ CameraX integration for camera access
- ✅ Background execution with wake lock (prevents sleep)
- ✅ Camera and audio permission handling
- ✅ Jetpack Compose UI foundation
- ✅ Project-local Gradle wrapper (isolated from system cache corruption)
- ✅ Clean APK generation and installation
- ✅ Emulator testing with webcam integration

**Technical Stack Confirmed:**
- Kotlin + Jetpack Compose
- CameraX for camera operations
- LifecycleService for background operation
- Android Foreground Service architecture

## Current Phase 🔄

### Phase 2: Intelligence Layer ✅ COMPLETED
**Status:** ML detection implemented but needs optimization
- ✅ Google ML Kit integration (Object, Face, Text detection)
- ✅ Motion detection algorithm
- ✅ Smart recording triggers
- ✅ Face image auto-save
- ⚠️ **Issue:** ML processing causing device freezing
- ⚠️ **Issue:** No user visibility into ML status

### Phase 2.5: Enhanced UI & ML Optimization ✅ COMPLETED
**Status:** Split-screen UI with verbose ML monitoring implemented
- ✅ Split-screen interface (camera feed + live status panel)
- ✅ Real-time ML status monitoring with color-coded logs
- ✅ Performance optimizations (frame skipping, throttling)
- ✅ Visual status overlays on camera feed
- ✅ Auto-scrolling status log with timestamps
- ✅ Memory management (100 message limit)
- ✅ Build issues resolved (Material3 API warnings fixed)

## Current Status: Ready for Testing 🚀
**Next Steps:**
- Install and test enhanced split-screen UI
- Verify ML detection performance and visibility
- Monitor for device freezing issues (should be resolved)

## Upcoming Phases 📋

### Phase 3: Video Recording & Storage
- Triggered recording on detection events
- Timestamped file naming system
- 100GB circular storage logic
- Minimal database for event logging

### Phase 4: User Interface
- Live camera feed display
- Zoomable timeline (0-24 hour view)
- Calendar date selection
- Event markers and playback

### Phase 5: Network Features (Optional)
- RTSP streaming for local network access
- HTTP endpoints for remote monitoring
- Integration with IR lighting systems

## Technical Challenges Resolved ✅
- **Gradle Cache Corruption:** Fixed with project-local wrapper isolation
- **Native Library Conflicts:** Resolved with clean build environment
- **APK Installation Failures:** Fixed with proper architecture configuration
- **Java 17+ Warnings:** Resolved with native access flags

## ML Kit Alternatives for Embedded Systems
*(For future AOSP/embedded deployments)*

### TensorFlow Lite Integration (Roadmap Item)
**Benefits:**
- No Google Play Services dependency
- Full offline operation
- GPU/NPU hardware acceleration support
- Self-contained APK

**Implementation Strategy:**
- Use GpuDelegate for modern chipsets
- Fallback to NnApiDelegate for Neural Processing Units  
- CPU threading for older devices
- Pre-trained models: MobileNet, YOLO variants

**Hardware Acceleration:**
- Qualcomm Snapdragon: Hexagon DSP + Adreno GPU
- MediaTek: APU (AI Processing Unit)
- Samsung Exynos: NPU integration
- Unisoc: AI acceleration support

## Development Environment
- **IDE:** Android Studio with clean Gradle installation
- **Emulator:** Configured with webcam access for real testing
- **Build System:** Project-isolated Gradle wrapper
- **Git Repository:** Private repository with proper .gitignore

---
**Last Updated:** August 16, 2025
**Current Status:** Phase 1 Complete ✅ | Phase 2 Ready to Begin 🚀
