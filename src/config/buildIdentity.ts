// Default identity for local builds. The manual release workflow overwrites
// this file before bundling when a different applicationId is requested.
export const BUILD_IDENTITY = {
  packageName: 'com.lxwalnut.music.mobile',
  providerAuthority: 'com.lxwalnut.music.mobile.provider',
  widgetActionPrefix: 'com.lxwalnut.music.mobile.widget',
  deepLinkScheme: 'lxmusic',
} as const
