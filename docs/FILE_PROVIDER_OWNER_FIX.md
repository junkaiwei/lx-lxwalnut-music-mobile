# FileProvider owner repair

## Scope

This change removes the `rn-fetch-blob` manifest Provider declaration during manifest merge. It keeps the project's `androidx.core.content.FileProvider` as the sole owner of `${APP_PACKAGE_NAME}.provider`, with `@xml/file_paths`, `android:exported="false"`, and `android:grantUriPermissions="true"` unchanged.

## Dependency and call-chain trace

`rn-fetch-blob` is used for downloads, file moves, media scanning, and image downloads. Those APIs use file-system and download-manager code; they do not use its `FileProvider`.

The dependency's `com.RNFetchBlob.Utils.FileProvider` exists only for `RNFetchBlob.actionViewIntent()`. That method constructs `${packageName}.provider` and requires the dependency's `external-path` XML. A source-wide search found no project or other dependency invocation of `actionViewIntent`; the app's APK updater instead calls `UtilsModule.installApk`, which explicitly receives the project `APP_PROVIDER_NAME` and uses the project Provider for files/cache paths.

## Why removal is safe for this revision

The merged Manifest previously contained two different Provider classes with the same authority. Android routed that authority to the project Provider, so RNFetchBlob's external root was not a reliable owner in the first place. Removing the unused declaration restores one unambiguous owner without changing RNFetchBlob download APIs or the project APK-install flow.

## Validation and rollback

- Build and inspect the merged Debug and minified Release manifests for default and overridden `APP_PACKAGE_NAME` values.
- Verify exactly one Provider has `<applicationId>.provider`, it is the project `androidx.core.content.FileProvider`, and its exported/grant/path settings are unchanged.
- Install and cold-start the relevant Debug/Release APKs; run the APK-update flow.
- Roll back this change if a supported caller needs `RNFetchBlob.actionViewIntent()`. In that case restore the dependency Provider and give it a distinct `${applicationId}.rnfetchblob.provider` authority, then patch that method through a maintained dependency override and validate external-file viewing.

## Executed validation

- JDK 17.0.19 / Android SDK 35: `:app:processDebugMainManifest` and `:app:processReleaseMainManifest` both completed successfully for the default identity. Each merged Manifest contains exactly one `androidx.core.content.FileProvider` using `com.lxwalnut.music.mobile.provider`, `exported=false`, `grantUriPermissions=true`, and `@xml/file_paths`; neither contains `com.RNFetchBlob.Utils.FileProvider`.
- Forced custom-identity manifest build: `:app:processDebugMainManifest -PAPP_PACKAGE_NAME=com.lxwalnut.music.providerfix -PAPP_DEEP_LINK_SCHEME=lxproviderfix --rerun-tasks` completed successfully. Its merged Manifest contains exactly one project Provider using `com.lxwalnut.music.providerfix.provider` and no RNFetchBlob Provider.
- Forced custom-identity Debug build: `:app:assembleDebug ... --rerun-tasks` completed successfully in 3m31s (456 tasks). The x86_64 APK reports package `com.lxwalnut.music.providerfix` and minSdk 21.
- API 23 emulator: the custom Debug APK installed with `adb install -r -t`, cold-started with `Status: ok` / `TotalTime: 2301`, and `dumpsys package` registered `com.lxwalnut.music.providerfix.provider` only for `androidx.core.content.FileProvider`. The temporary test package was then uninstalled.
- Release shrinking: `:app:minifyReleaseWithR8` completed successfully in 2m50s. Producing a signed distributable Release APK remains delegated to the protected integration workflow because this branch has no signing secret material.
