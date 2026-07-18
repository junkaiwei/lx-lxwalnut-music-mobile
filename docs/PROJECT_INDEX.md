# AI Project Index

## Build identity

Single source:
- `APP_PACKAGE_NAME`
- `APP_DEEP_LINK_SCHEME`

Affected:
- Android applicationId
- FileProvider authority
- Widget action prefix
- JavaScript build identity

Generation:
- `scripts/generate-build-identity.js`
- Gradle runs generation before Android build

## Application entry

Flow:
```
index.js
 -> src/app
 -> src/core/init
 -> navigation initialization
 -> home screen
```

## React Native layers

`src/screens`
- UI pages

`src/store`
- application state

`src/plugins/player`
- playback service
- react-native-track-player integration

`src/utils/nativeModules`
- bridge wrappers for native features

## Android native

`android/app/src/main`

Important:

- `AndroidManifest.xml`
  - FileProvider
  - Widget Provider
  - Deep links

- `widget/`
  - MusicWidgetProvider
  - MusicWidgetModule
  - WidgetPackage

Communication:
```
Desktop Widget
 -> MusicWidgetProvider
 -> internal broadcast
 -> MusicWidgetModule
 -> NativeEventEmitter
 -> JS player controls
```

## Build and CI

Workflow:
- `.github/workflows/packet-name-build.yml`

Required secrets:
- SIGN_KEYSTORE_BASE64
- SIGN_STORE_PASSWORD
- SIGN_KEY_ALIAS
- SIGN_KEY_PASSWORD

## Feature development rules

Base branch:
`build/packet-name`

Sync branch:
`main-debug`

Do not build new features from `main-debug`.
