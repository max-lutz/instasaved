# InstaSaved

An Android app that shows only your saved Instagram posts — in an Instagram-like grid — and lets you organize them: Collections, Tags, notes. It syncs automatically from Instagram's scheduled data Export in your Google Drive. Everything stays on your phone.

Successor to [Socials Organizer](https://github.com/max-lutz/instagram-organizer) (desktop, paused).

- Domain language: [`CONTEXT.md`](CONTEXT.md)
- Product scope: [`docs/PRD.md`](docs/PRD.md)
- Sync rules: [`docs/sync-spec.md`](docs/sync-spec.md)
- Decisions: [`docs/adr/`](docs/adr/)
- Backlog: [`docs/backlog.md`](docs/backlog.md)

## Stack

Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · Google Identity `AuthorizationClient` + Drive REST API (`drive.readonly`) · Coil · WebView. Min Android 10 (API 29). See ADR-0002.

## One-time setup

### 1. Instagram scheduled Export

Instagram → Accounts Center → Your information and permissions → Export your information → Create export:
- Account: your Instagram account
- **Export to external service → Google Drive** (connect your Google account)
- Information: customize → **Saved** only
- Date range: **All time** (required — Sync detects unsaved posts by comparing complete snapshots)
- Frequency: **Daily**
- Format: **JSON**

### 2. Google Cloud project (developer, once)

1. Create a Google Cloud project; enable the **Google Drive API**.
2. OAuth consent screen: External user type; add the scope `https://www.googleapis.com/auth/drive.readonly`; **publish to "In production"** — don't submit for verification (ADR-0004). "Testing" would expire sign-ins every 7 days.
3. Create **Android** OAuth clients for package `com.maxlutz.instasaved`, one per signing key:
   - debug key: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - release key: the SHA-1 of the release keystore used for GitHub Releases. Keep that keystore safe: a new key means a new OAuth client and broken updates.

### 3. Install

Add this repo's GitHub Releases to [Obtainium](https://github.com/ImranR98/Obtainium), or download the APK directly. On first launch, connect Google Drive and click through "Google hasn't verified this app" (Advanced → continue).

## Build

Android Studio (latest stable), JDK 21.

```
./gradlew assembleDebug
./gradlew test
```

## Release

CI runs `./gradlew test assembleRelease` on every push. Pushing a tag `vMAJOR.MINOR.PATCH` (e.g. `v0.1.0`) builds a signed APK and publishes it as a GitHub Release, which is what Obtainium follows. The version name comes from the tag; the version code is `MAJOR*10000 + MINOR*100 + PATCH`, so minor and patch stay below 100.

The release keystore lives outside the repo and is passed to the Release workflow through these repository secrets:

| Secret | Value |
| --- | --- |
| `INSTASAVED_KEYSTORE_BASE64` | the `.jks` file, base64-encoded |
| `INSTASAVED_KEYSTORE_PASSWORD` | keystore password |
| `INSTASAVED_KEY_ALIAS` | key alias |
| `INSTASAVED_KEY_PASSWORD` | key password |

To build a signed APK locally, set the same variables, with `INSTASAVED_KEYSTORE_PATH` pointing to the `.jks` file instead of the base64 value. Without them, `assembleRelease` produces an unsigned APK.

## Privacy

The app's own data — posts, notes, tags, collections — is never uploaded anywhere, apart from Android's automatic backup to your Google account. The app only downloads: the Export from your Drive, and thumbnails and embeds from Instagram. No analytics, no crash reporting.
