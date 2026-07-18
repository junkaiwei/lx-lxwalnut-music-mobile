# Runtime architecture

## Branches

- `main-debug`: upstream sync only.
- `build/packet-name`: long-lived development branch.
- New feature branches start from the latest `build/packet-name`.

## Startup

```text
index.js -> src/app -> src/core/init -> navigation and player
```

- `src/app` initializes logs, global data, and navigation.
- `src/core/init` initializes settings, theme, APIs, player, data, sync, and deep links.
- Screens are registered under `src/navigation`.

## Playback

- Playback uses `react-native-track-player`.
- Service registration: `src/plugins/player/service.ts`.
- Core controls: `src/core/player`.
- UI, widget, notification, and future media controls must share these actions.

## Widget communication

```text
Desktop widget
 -> MusicWidgetProvider
 -> package-scoped internal broadcast
 -> MusicWidgetModule
 -> NativeEventEmitter
 -> JS player controls