# Surveillance Camera App - Development Roadmap

## Project Overview
A power-efficient Android security camera app that runs locally on the device, designed to eliminate Android e-waste by repurposing old devices into surveillance systems.

## Phase 1: Core Service ✅ **COMPLETED**
- [x] Foreground Service implementation with proper lifecycle management
- [x] Camera access and preview system (CameraX integration)
- [x] Permission handling (Camera, Audio, Foreground Service, Battery Optimization)
- [x] Service-only camera architecture (no UI camera conflicts)
- [x] Background operation with wake locks
- [x] Notification system for service status

**Key Achievement**: Service runs continuously in background, even with screen off.

## Phase 2: ML Intelligence 🔄 **IN PROGRESS**
- [x] Google ML Kit integration (Object Detection, Face Detection, Text Recognition)
- [x] On-device, hardware-accelerated processing
- [x] Motion detection using pixel comparison algorithms
- [x] Real-time ML callbacks and data forwarding
- [x] Performance optimization (frame skipping, throttling)
- [x] UI display of detection results
- [ ] **CURRENT ISSUE**: Motion detection stuck at 0.000 (service-to-UI data flow needs debugging)
- [ ] Fine-tune detection thresholds and performance
- [ ] Implement recording triggers based on ML analysis

**Status**: Core ML infrastructure working, troubleshooting motion detection display.

## Phase 3: Video Recording & Storage 📅 **PLANNED**
- [ ] Implement actual video recording (currently simulated)
- [ ] Circular storage system with 100GB limit
- [ ] Automatic oldest file deletion
- [ ] Video compression and optimization
- [ ] Database for event logging and metadata
- [ ] Face image saving with timestamps
- [ ] OCR license plate detection and storage

## Phase 4: User Interface 📅 **PLANNED**
- [ ] Jetpack Compose timeline interface
- [ ] Clickable, zoomable event timeline
- [ ] Calendar date selection
- [ ] Video playback controls
- [ ] Event filtering and search
- [ ] Settings and configuration UI
- [ ] Export and sharing functionality

## Phase 5: Advanced Features 📅 **FUTURE**
- [ ] RTSP streaming support for network access
- [ ] Integration with 3rd party IR lights for night vision
- [ ] HTTP API for home automation integration
- [ ] Video rotation for upside-down mounting
- [ ] Face recognition and cropping tools
- [ ] Object tracking and annotation
- [ ] Push notifications for critical events

## Technical Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Camera**: CameraX
- **ML Processing**: Google ML Kit (on-device)
- **Background Processing**: Foreground Services
- **Architecture Pattern**: Service-first design (camera logic in service, UI for display only)

## Current Development Challenges
1. **Motion Detection Data Flow**: Service processes motion correctly but UI shows 0.000
2. **Cache Management**: Build system requires frequent cache clearing for proper updates
3. **Service-UI Communication**: Optimizing data transfer between service and UI components
4. **Performance Tuning**: Balancing ML processing frequency with battery efficiency

## Offline ML Model Support (Future Consideration)
For devices without Google Play Services, we've identified the possibility of:
- Including TensorFlow Lite models directly in the APK
- GPU acceleration through device-specific drivers
- Custom model training for specific use cases
- Fallback CPU processing for older devices

## Key Milestones
- ✅ **August 2024**: Basic service architecture and camera preview
- 🔄 **August 2024**: ML detection integration and debugging
- 📅 **September 2024**: Video recording implementation
- 📅 **October 2024**: UI timeline and playback features
- 📅 **November 2024**: Advanced features and optimization

---
*Last Updated: August 20, 2024*
*Status: Phase 2 - ML Intelligence (Motion Detection Debugging)*
