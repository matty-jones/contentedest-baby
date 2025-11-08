# OTA Update System

This document describes how to use the Over-The-Air (OTA) update system for The Contentedest Baby app.

## Overview

The update system allows you to push new versions of the app to devices without going through the Play Store. When a new version is available, users will be prompted to update when they open the app.

## How It Works

1. **Server Endpoint**: The server exposes `/app/update` which returns the latest version information
2. **Update Check**: The Android app checks for updates on startup
3. **Download & Install**: If an update is available, the app downloads the APK and prompts the user to install it

## Server Setup

### Automated Release (Recommended)

Use the automated release script for a hands-off release process:

```bash
./release_application
```

This script will:
- Automatically increment the minor version (e.g., 1.0 → 1.1)
- Increment versionCode by 1
- Update `android/app/build.gradle.kts`
- Build the release APK
- Copy APK to `server/apks/latest.apk`
- Update `server/app/main.py` with new version info

See `RELEASE_GUIDE.md` for detailed usage instructions.

### Manual Release Process

If you need to manually release:

#### 1. Update Version Information

Edit `server/app/main.py` and update the `get_update_info()` function:

```python
@app.get("/app/update", response_model=UpdateInfoResponse)
def get_update_info():
    base_url = os.getenv("BASE_URL", "http://192.168.86.3:8005")
    
    return UpdateInfoResponse(
        version_code=2,  # Increment this for each new release
        version_name="1.1",  # Human-readable version
        download_url=f"{base_url}/app/download/latest.apk",
        release_notes="Bug fixes and improvements",
        mandatory=False  # Set to True to force updates
    )
```

**Important**: Always increment `version_code` when releasing a new version. The Android app compares this with `BuildConfig.VERSION_CODE` to determine if an update is available.

#### 2. Update Android Version

Before building a new APK, update the version in `android/app/build.gradle.kts`:

```kotlin
defaultConfig {
    // ...
    versionCode = 2  // Increment this
    versionName = "1.1"  // Update this
    // ...
}
```

**Critical**: The `versionCode` in `build.gradle.kts` must match the `version_code` in the server's `/app/update` endpoint.

#### 3. Build and Upload APK

1. Build a release APK:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```

2. The APK will be at: `android/app/build/outputs/apk/release/app-release.apk`

3. Copy the APK to the server:
   ```bash
   cp android/app/build/outputs/apk/release/app-release.apk server/apks/latest.apk
   ```

4. Make sure the `server/apks/` directory exists and is readable by the server process

## Update Flow

1. **User opens app**: The app checks `/app/update` on startup
2. **Update available**: If server `version_code > app version_code`, a dialog appears
3. **User taps "Update"**: 
   - Progress dialog shows download progress
   - APK downloads to app cache
   - Android system prompts user to install
4. **Installation**: User confirms installation in Android's system dialog
5. **App restarts**: After installation, user can launch the updated app

## Mandatory Updates

Set `mandatory: True` in the server response to prevent users from dismissing the update dialog. They must update to continue using the app.

## Testing

1. Build an APK with `versionCode = 1`
2. Install it on a device
3. Update server to return `version_code = 2`
4. Place a new APK in `server/apks/latest.apk`
5. Open the app - you should see the update dialog

## Troubleshooting

### Update dialog doesn't appear
- Check that server `version_code` > app `version_code`
- Verify `/app/update` endpoint is accessible
- Check Android logcat for errors: `adb logcat | grep UpdateChecker`

### Download fails
- Verify APK exists at `server/apks/latest.apk`
- Check server logs for errors
- Ensure network connectivity

### Installation fails
- User may need to enable "Install from unknown sources" in Android settings
- Check that FileProvider is configured correctly in `AndroidManifest.xml`
- Verify APK file is not corrupted

## Security Considerations

- The update system uses HTTP (not HTTPS) since it's for local network use
- APK files are served directly from the server
- Consider adding APK signature verification in the future for production use
- The system trusts the server - ensure your server is secure

## Future Enhancements

- [ ] Add APK signature verification
- [ ] Support incremental updates
- [ ] Add download progress tracking
- [ ] Support rollback to previous versions
- [ ] Add update scheduling (e.g., check daily instead of on every startup)

