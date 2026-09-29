# MASTER ROOT — Android Root Assistant
## v1.0 — Build & Setup Guide

---

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- A physical Android device (emulators cannot test ADB connections)

---

## Project Setup

### 1. Open in Android Studio

```
File → Open → select the `masterroot/` folder
```

### 2. Sync Gradle

Android Studio will prompt you to sync. Allow it.

### 3. Add the magiskboot Binary (REQUIRED)

The patcher requires the real `magiskboot` binary from an official Magisk release.

1. Download the latest official Magisk APK from: https://github.com/topjohnwu/Magisk/releases
2. Rename `.apk` to `.zip` and extract it
3. Find `lib/<abi>/libmagiskboot.so` for each architecture
4. Copy and rename to:

```
app/src/main/assets/magiskboot/arm64-v8a/magiskboot
app/src/main/assets/magiskboot/armeabi-v7a/magiskboot
app/src/main/assets/magiskboot/x86_64/magiskboot
app/src/main/assets/magiskboot/x86/magiskboot
```

**Verify the binary SHA-256 against the official Magisk release before bundling.**

### 4. Configure AdMob (for release)

In `app/build.gradle.kts`, replace:
```kotlin
buildConfigField("String", "ADMOB_APP_ID", "\"YOUR_PRODUCTION_ADMOB_APP_ID\"")
```

And in `ProductionAdIds` in `AdManager.kt`:
```kotlin
const val BANNER       = "ca-app-pub-XXXXXX/XXXXXXXX"
const val INTERSTITIAL = "ca-app-pub-XXXXXX/XXXXXXXX"
```

Debug builds automatically use Google's official test ad IDs — safe to test freely.

### 5. App Signing (for release)

Generate a keystore:
```bash
keytool -genkey -v -keystore masterroot-release.jks -alias masterroot \
  -keyalg RSA -keysize 2048 -validity 10000
```

Add to `app/build.gradle.kts` signingConfigs before release.

---

## Architecture Overview

```
MASTER ROOT
│
├── domain/model/           ← Pure data types, state machine states
├── domain/usecase/         ← Business logic (RootWorkflow, MagiskPatcher)
│
├── infrastructure/
│   ├── adb/                ← ConnectionManager, DeviceInfoParser, BootImageAnalyzer
│   ├── service/            ← Foreground services (Wireless ADB, Root Operation)
│   └── storage/            ← Log + Backup repositories
│
├── presentation/
│   ├── home/               ← Dashboard
│   ├── root/               ← Root workflow UI (state machine driven)
│   ├── device/             ← USB + Wireless connection screens
│   ├── logs/               ← Real-time log viewer
│   ├── recovery/           ← Recovery center
│   └── settings/           ← Settings, supported devices
│
└── monetization/           ← AdMob + Premium (isolated from root engine)
```

---

## Key Implementation Notes

### ADB Execution

The app calls the `adb` binary that must be present on the host machine (not bundled in the APK). For the target device (the one being rooted), commands execute via:

- USB: `adb shell <command>`
- Wireless: `adb -s <ip>:<port> shell <command>`

### Fastboot Flash

Installation uses `fastboot flash boot <patched.img>`. The device must be in fastboot mode for this. The workflow checks bootloader state and handles this transition.

### Boot Image Compatibility

The strict compatibility checker uses:
1. SHA-256 of exact build fingerprint match (strongest)
2. Manufacturer match from fingerprint prefix
3. CPU architecture from image header
4. Android SDK version

If metadata is insufficient → BLOCKED.

### Supported Devices (v1.0)

Only explicitly listed devices are SUPPORTED. All others are UNSUPPORTED.
Add new devices in `DeviceCompatibilityChecker.kt` after testing.

---

## What Requires Additional Work Before Shipping

1. **magiskboot binaries** — must be sourced from official Magisk releases
2. **AdMob App ID** — register at https://admob.google.com
3. **Fastboot mode transition** — the workflow needs UI to guide user into fastboot mode
4. **UMP Consent SDK** — add Google's User Messaging Platform for GDPR compliance
5. **App signing** — configure release keystore
6. **Icon assets** — replace placeholder ic_launcher with production assets

---

## Safety Guarantee

Per the product specification:

- SUCCESS is only reported after real, verified root access
- FAILURE stops safely — never converted to success
- UNKNOWN states block all operations
- Original boot image is never overwritten
- Unsupported devices are blocked before any operation
- No fake progress, no fake root, no fake success

---

*MASTER ROOT — Root Your Supported Phone.*

## Build-ready notes

This source package includes the Android/Gradle project structure and launcher resources. Google AdMob test IDs are used by default so a fresh debug/release build does not contain placeholder values. Replace them with your own production IDs before publishing.

The real `magiskboot` executable is intentionally not bundled. The app detects its absence and blocks patching rather than pretending a patch succeeded. Add verified binaries under `app/src/main/assets/magiskboot/<abi>/magiskboot` when you are ready to enable that workflow.

If Android Studio asks for a Gradle distribution, use **Gradle 8.6** with **JDK 17** and Android SDK 34.
