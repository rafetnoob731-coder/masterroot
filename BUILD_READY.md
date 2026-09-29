# MASTER ROOT — Build-ready package

## Android Studio
1. Open the `masterroot` directory (the directory containing `settings.gradle.kts`).
2. Use JDK 17.
3. Install Android SDK Platform 34 and Build-Tools through SDK Manager.
4. Allow Gradle sync to download dependencies.
5. Build > Make Project.
6. Build > Build APK(s) > Debug APK.

## Important
- AdMob uses Google's test IDs by default in this package. Replace them before publishing.
- `magiskboot` is intentionally not included because it must be sourced and verified from an official Magisk release. Until added, the patching workflow will fail safely.
- No release keystore is included.
