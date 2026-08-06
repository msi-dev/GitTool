# 🛠️ GitTool

[![GitHub Release](https://img.shields.io/github/v/release/msi-dev/GitTool?logo=github&style=flat-square&color=blue)](https://github.com/msi-dev/GitTool/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?logo=android&style=flat-square)](https://android-sdk.support/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg?logo=kotlin&style=flat-square)](https://kotlinlang.org/)

**GitTool**  A high-performance, feature-rich Android client designed for power users and developers to manage, browse, and sync their GitHub spaces directly from their mobile devices.

---

##  Login UI

<p align="center">
  <img src="https://raw.githubusercontent.com/msi-dev/GitTool/refs/heads/main/login.jpg" width="100%" alt="login" />
  
---

##  Dashboard

<p align="center">
  <img src="https://raw.githubusercontent.com/msi-dev/GitTool/refs/heads/main/dashboard.jpg" width="100%" alt="Repository Dashboard" />
  
---

## Code Preview

  <img src="https://raw.githubusercontent.com/msi-dev/GitTool/refs/heads/main/code.jpg" width="100%" alt="Code Browser" />

---

## Profile
  
  <img src="https://raw.githubusercontent.com/msi-dev/GitTool/refs/heads/main/profile.jpg" width="100%" alt="User Profile" />
</p>

---

## Features

- **Robust Secure Login**: `GitHub OAuth authorization` or login directly using `Personal Access Tokens` (PAT).
- **Public & Private Repositories**: Navigate Your project Separately.
- **Folder Upload Engine**: You can push your project easyly selecting project folder.
- **Import and Fork**: Fork any repo Using Url and Manage Separately.
- **Interactive File Browser**: Open Any project directly by The app and view source.
- **Live In-App Notifications**: Stay up to date with real-time GitHub notifications.
- **Global Search Engine**: Global searches for any developer or repository across the entirety of GitHub with dynamic results.
- **Profile Management**: View stats, biographies, organization memberships, repositories, followers, following counts, and update user bios directly.
- **Modern Material 3 Theming**: Responsive fluid Edge-to-Edge display support, custom dynamic colors, and smooth switching between Light, Dark, and System Theme presets.
- **NDK Security & splits**: Uses low-level Play Integrity helpers and C++ binary integrity checks to secure API calls and tokens. Supports multi-ABI APK splitting to ensure compact install sizes.

---

## Downloads

To run or try GitTool, get the pre-built configurations:

<p align="left">
  <a href="https://play.google.com/store/apps/details?id=com.msi.gittool">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="220" alt="Get it on Google Play" />
  </a>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <a href="https://github.com/msi-dev/GitTool/releases">
    <img src="https://img.shields.io/badge/Download_APK-GitHub_Releases-gray?style=for-the-badge&logo=github" height="65" alt="Download APK from GitHub" />
  </a>
</p>

---

## How to Build

Follow these simple phases to build and deploy GitTool locally:

### 1. Prerequisites
- **JDK 17** or higher
- **Android Studio Ladybug** or newer
- **Android SDK** installed (Targeting API Level 34/36)

### 2. Configure Environment Variables
Copy `.env.example` to `.env` in the root of the project:

```bash
cp .env.example .env
```

Open `.env` and fill in your GitHub Application credentials (register your application under GitHub Developer settings with the redirect URI `gittool://callback`):

```env
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
```

### 3. Signing Setup (Optional)
To sign your application with production keys, create `app/keystore.properties` in your project with the following fields:

```properties
storeFile=msi.gittool.jks
storePassword=your_keystore_password
keyAlias=your_alias
keyPassword=your_key_password
```

Ensure `msi.gittool.jks` resides inside the `app/` directory (or specify its relative path). If `keystore.properties` is omitted, the build automatically falls back to your local debug keystore keys!

### 4. Build and Install Tasks
To build a signed Debug APK:
```bash
gradle assembleDebug
```
To build a fully-optimized, ProGuard-minified Release APK:
```bash
gradle assembleRelease
```

The compiled APKs will be located under:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## Tech Stack

- **UI Framework**: Modern Jetpack Compose with declarative layouts
- **Local Database**: Jetpack Room for local persistence, bookmarks, and user caching
- **DI Container**: Simple constructor dependency injection for ease of unit testing
- **Network Stack**: Retrofit 2 + OkHttp 3 for robust HTTP request management
- **OAuth Protocol**: RFC 8252 standard Client Authorization implemented with `AppAuth-Android`
- **Crypto & Security**: `Jetpack Security-Crypto` (EncryptedSharedPreferences) and low-level NDK binary verification
- **Asynchrony**: Kotlin Coroutines & Flow structures with state hoisting
- **Build System**: Gradle Kotlin DSL structured around Centralized Catalog (`libs.versions.toml`)

---

## License

GitTool is released under the **MIT License**. Check the [LICENSE](LICENSE) file for more information.
