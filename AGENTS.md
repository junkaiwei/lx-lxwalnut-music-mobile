# Agent development guide

This is the first file coding agents should read.

Before non-trivial changes read:
- DEVELOPMENT.md
- docs/PROJECT_INDEX.md
- docs/ARCHITECTURE.md

## Branch rules

- `main-debug`: upstream synchronization only.
- `build/packet-name`: primary development and integration branch.
- Never develop features directly on `main-debug`.
- Sync upstream into `main-debug`, merge into `build/packet-name`, then create feature branches.

## Identity rules

`APP_PACKAGE_NAME` is the single source of truth for:

- Android applicationId
- FileProvider authority
- Widget action prefix
- JavaScript build identity

Do not hardcode package names in Android or JS communication code.

## Important areas

- `src/core/player`: playback logic
- `src/plugins/player`: TrackPlayer service
- `src/utils/nativeModules`: JS/native bridge
- `android/app/src/main`: Android integration
- `android/app/src/main/java/.../widget`: desktop widget bridge
- `.github/workflows`: packet-name CI

## Validation

After Android identity changes verify:
- final APK package name
- Provider authority
- Widget actions
- signing certificate
- runtime widget communication
