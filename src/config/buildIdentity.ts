// Default identity for local builds.
// CI may overwrite this file before bundling.
export const BUILD_IDENTITY = {
  packageName: 'com.lxwalnut.music.mobile',
  providerAuthority: 'com.lxwalnut.music.mobile.provider',
  widgetActionPrefix: 'com.lxwalnut.music.mobile.widget',
  deepLinkScheme: 'lxmusic',
} as const
