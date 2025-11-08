# Release Guide

This guide explains how to use the automated release script to build and deploy new versions of The Contentedest Baby app.

## Quick Start

Simply run the release script from the project root:

```bash
./release_application
```

The script will:
1. Extract the current version from `android/app/build.gradle.kts`
2. Increment the minor version (e.g., 1.0 → 1.1) and version code
3. Update `build.gradle.kts` with the new version
4. Build the release APK
5. Copy the APK to `server/apks/latest.apk`
6. Update `server/app/main.py` with the new version information

## What Gets Updated

### Android (`android/app/build.gradle.kts`)
- `versionCode`: Incremented by 1 (e.g., 1 → 2)
- `versionName`: Minor version incremented (e.g., "1.0" → "1.1")

### Server (`server/app/main.py`)
- `version_code`: Updated to match Android versionCode
- `version_name`: Updated to match Android versionName

### APK Output
- Built APK: `android/app/build/outputs/apk/release/app-release.apk`
- Server copy: `server/apks/latest.apk`

## Version Numbering

The script uses **semantic versioning** with automatic minor version increments:

- **Major.Minor** format (e.g., 1.0, 1.1, 1.2)
- Minor version increments automatically (1.0 → 1.1 → 1.2)
- Version code increments by 1 each release (1 → 2 → 3)

### Example Release Flow

```
Release 1: versionCode=1, versionName="1.0"
Release 2: versionCode=2, versionName="1.1"  ← Minor increment
Release 3: versionCode=3, versionName="1.2"  ← Minor increment
```

## Prerequisites

1. **Android SDK**: Gradle and Android build tools must be installed
2. **Java 17**: Required for building the Android app
3. **Server directory**: `server/apks/` directory will be created automatically

## Usage

### Basic Release

```bash
./release_application
```

The script will:
- Show current and new version numbers
- Ask for confirmation before proceeding
- Build the APK (may take a few minutes)
- Update all necessary files

### After Release

1. **Review changes**: Check the updated files to ensure everything looks correct
2. **Restart server** (if running as a service):
   ```bash
   sudo systemctl restart contentedest-baby.service
   ```
3. **Test update**: Open the app on a device - it should prompt for update

## Troubleshooting

### Build Fails

- Ensure Android SDK is properly configured
- Check that Java 17 is installed and set as JAVA_HOME
- Verify Gradle wrapper is executable: `chmod +x android/gradlew`

### Version Extraction Fails

- Ensure `build.gradle.kts` has `versionCode` and `versionName` in the `defaultConfig` block
- Check that the file format matches expected Kotlin syntax

### APK Not Found After Build

- Check `android/app/build/outputs/apk/release/` directory
- Verify build completed successfully (check for errors in output)
- Ensure you're building the `release` variant

### Server Update Fails

- Verify `server/app/main.py` has the `get_update_info()` function
- Check that the version fields are in the expected format
- Ensure you have write permissions to the file

## Manual Version Override

If you need to manually set a version (e.g., for a major version bump), you can:

1. Edit `android/app/build.gradle.kts` manually
2. Edit `server/app/main.py` manually
3. Build APK: `cd android && ./gradlew assembleRelease`
4. Copy APK: `cp android/app/build/outputs/apk/release/app-release.apk server/apks/latest.apk`

## Future Enhancements

Planned features:
- `--major` flag to increment major version (e.g., 1.0 → 2.0)
- `--patch` flag for patch versions (e.g., 1.0 → 1.0.1)
- `--release-notes` flag to add release notes during release
- `--mandatory` flag to mark update as mandatory
- Automatic git tagging
- Release changelog generation

## Notes

- The script creates a backup-free release process - always commit your changes before running
- APK files can be large - ensure you have sufficient disk space
- The script will prompt for confirmation before making changes
- All file updates use `sed` - ensure files are in the expected format

