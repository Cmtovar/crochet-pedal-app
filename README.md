# Pro Fold Apps Template

A Jetpack Compose starter for building Android apps optimized for foldable devices. Includes Material 3 adaptive layouts, release signing CI/CD, and a built-in in-app updater that checks GitHub Releases.

## What's Included

- **Foldable-aware layouts** — `WindowSizeClass` detection with compact (folded) and expanded (unfolded) UI
- **Material 3 + Material You** — Dynamic color theming
- **CI/CD** — GitHub Actions builds a signed release APK and creates a GitHub Release on every push to `main`
- **In-app updater** — Checks GitHub Releases for new versions on launch, downloads and installs updates without needing to uninstall

## Getting Started

### 1. Copy the template

Copy this directory into a new folder for your app:

```bash
cp -r templates/pro-fold-apps-template my-new-app
cd my-new-app
git init
```

### 2. Rename the package

Replace `com.profold.app` with your package name in these files:

- `app/build.gradle.kts` — `namespace` and `applicationId`
- `app/src/main/AndroidManifest.xml`
- All Kotlin files under `app/src/main/java/com/profold/app/`

Then move the source files to match your new package directory structure.

### 3. Set the GitHub repo for updates

In `app/build.gradle.kts`, update the `GITHUB_REPO` build config field to your `"owner/repo"`:

```kotlin
buildConfigField("String", "GITHUB_REPO", "\"YourUser/your-repo\"")
```

The in-app updater uses this to check for new releases via the GitHub API.

### 4. Set up release signing

Generate a keystore (one time):

```bash
keytool -genkey -v -keystore release.keystore \
  -alias myapp -keyalg RSA -keysize 2048 -validity 10000
```

Add these secrets to your GitHub repo (Settings > Secrets and variables > Actions):

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -i release.keystore` output |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `myapp`) |
| `KEY_PASSWORD` | Key password |

This ensures every release APK is signed with the same key, so updates install cleanly over previous versions without uninstalling.

### 5. Push and release

```bash
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/YourUser/your-repo.git
git push -u origin main
```

GitHub Actions will build a signed APK and create a release automatically. Download the APK from the Releases page on your phone.

## Project Structure

```
app/
├── build.gradle.kts              # Dependencies, signing, build config
└── src/main/
    ├── AndroidManifest.xml       # Permissions + FileProvider
    ├── res/
    │   └── xml/file_provider_paths.xml  # For APK update downloads
    └── java/com/profold/app/
        ├── MainActivity.kt       # Adaptive UI + update dialog
        ├── AppUpdater.kt         # GitHub release checker + installer
        └── ui/theme/Theme.kt     # Material You theming
```

## How the In-App Updater Works

On launch, the app calls the GitHub Releases API to check for a newer version. It compares the `versionName` in your `build.gradle.kts` against the latest release tag (stripping the `v` prefix and `-build.N` suffix).

If a newer version exists:
1. A dialog appears showing the available version
2. Tapping "Update" downloads the APK to the app's cache
3. The Android package installer opens to install it

To trigger an update, bump `versionName` in `build.gradle.kts` and push to `main`.

## Bumping Versions

Update these in `app/build.gradle.kts` before releasing:

```kotlin
versionCode = 2          // Increment for each release (Android requires this)
versionName = "1.1.0"    // Semver — the updater compares this
```

## Dependencies

| Library | Purpose |
|---------|---------|
| Compose BOM 2024.12.01 | UI toolkit |
| Material 3 + WindowSizeClass | Adaptive foldable layouts |
| Window Manager 1.3.0 | Fold state detection |
| OkHttp 4.12.0 | HTTP client (updater) |
| Gson 2.11.0 | JSON parsing (updater) |

## Requirements

- Android Studio with JDK 17
- `minSdk` 30 (Android 11+)
- `targetSdk` / `compileSdk` 35
