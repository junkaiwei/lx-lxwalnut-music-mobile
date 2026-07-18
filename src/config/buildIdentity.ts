// Default identity for local builds.
// Package name is the single source of truth.
export const BUILD_IDENTITY = {
  packageName: 'com.lxwalnut.music.mobile',
  providerAuthority: 'com.lxwalnut.music.mobile.provider',
  widgetActionPrefix: 'com.lxwalnut.music.mobile.widget',
  deepLinkScheme: 'lxmusic',
} as const
