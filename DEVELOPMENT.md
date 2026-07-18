# Development workflow

## Long-lived branches

- `main-debug`: fork synchronization branch only. Keep it aligned with the upstream repository. Do not develop project features directly on this branch.
- `build/packet-name`: primary development and integration branch. It contains package-name configurability, dynamic provider/widget identity, fixed signing support, and all project-specific development.

## Required workflow

1. Synchronize upstream changes into `main-debug`.
2. Merge the updated `main-debug` into `build/packet-name`.
3. Create each feature or fix branch from the latest `build/packet-name`.
4. Develop and validate the change on that feature branch.
5. Merge the completed change back into `build/packet-name`.

Do not use `main-debug` as the base for new project features.

## Android application identity

`APP_PACKAGE_NAME` is the single source of truth for:

- Android `applicationId`
- FileProvider authority: `${APP_PACKAGE_NAME}.provider`
- Widget action prefix: `${APP_PACKAGE_NAME}.widget`
- JavaScript build identity

`APP_DEEP_LINK_SCHEME` controls the deep-link scheme.

Gradle invokes `scripts/generate-build-identity.js` before every Android build, so local and CI builds use the same identity values.

Example:

```bash
cd android
./gradlew assembleRelease \
  -PAPP_PACKAGE_NAME=com.tencent.qqmusic \
  -PAPP_DEEP_LINK_SCHEME=lxmusic
```

## CI builds

The Android package workflow is stored and triggered from `build/packet-name`. It uses the repository secrets:

- `SIGN_KEYSTORE_BASE64`
- `SIGN_STORE_PASSWORD`
- `SIGN_KEY_ALIAS`
- `SIGN_KEY_PASSWORD`

The workflow must verify the generated package name and signing certificate before uploading APK artifacts.
