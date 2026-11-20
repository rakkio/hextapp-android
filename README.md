# Hextapp Android

Official native Android client for **Hextapp** (`com.hextapp.android`), built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **Apollo GraphQL**.

## Features

- **Native Catalog & Discovery**: Browse featured, trending, and categorized Android APKs and games (`HomeScreen`, `ExploreScreen`, `GamesScreen`).
- **App Details & Direct Downloads**: Rich app detail pages (`AppDetailScreen`) with built-in APK downloading and one-tap installation (`ApkDownloadManager`).
- **Personal Library & Account Sync**: Manage bookmarks, favorites, and download history (`LibraryScreen`, `ProfileScreen`).
- **Adaptive Theme**: Persistent Light & Dark Material 3 theme (`ThemePreferences`).

## Tech Stack

- **Language**: Kotlin (JVM 17)
- **UI**: Jetpack Compose + Material 3 + Navigation Compose
- **Networking**: Apollo Kotlin GraphQL (`https://api.hextapp.com/graphql`) + OkHttp
- **Image Loading**: Coil 3
- **Persistence**: Jetpack DataStore Preferences

## Build

```bash
./gradlew assembleDebug
```

The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
