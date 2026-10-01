# Native Android: Kotlin + Jetpack Compose

The app targets Android only. It is built natively with Kotlin, Jetpack Compose (Material 3, custom Instagram-like styling), Room for the database, WorkManager for the daily Sync, Google Identity's `AuthorizationClient` for Drive access, Coil for Thumbnails, and a `WebView` for Embeds. Minimum Android version: 10 (API 29). One app module to start; no dependency-injection framework until it earns its place.

The things this app leans on — background scheduling, the share sheet, file access, Google sign-in — are all first-class in native Android and plugin-shaped everywhere else. React Native/Expo would have kept the codebase in TypeScript and let the desktop domain logic port over, and Capacitor would have kept the existing web UI; neither advantage holds up once the UI is being redesigned for mobile anyway and iOS is out of scope.

The domain rules (Title auto-follow, Sync rules, Deleted Posts) are rewritten in Kotlin and kept as pure, unit-tested code independent of Android APIs.
