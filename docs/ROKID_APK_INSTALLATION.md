# JSOS HUD APK Installation Notes

This document describes the public JSOS HUD installation path.

## Current Status

JSOS Core and JSOS HUD are treated as separate APKs:

1. Build the phone app.
2. Build the glasses app.
3. Install JSOS Core on the phone.
4. Install JSOS HUD on the glasses through Hi Rokid / APK Manager.

JSOS Core no longer bundles the glasses APK into `phone-app/src/main/assets/`.
JSOS does not currently document direct `CxrApi.startUploadApk()` installation as the supported public path.

## Build Outputs

For local debug testing:

```powershell
.\gradlew.bat :phone-app:assembleDebug :glasses-app:assembleDebug
```

Expected APK outputs:

```text
phone-app/build/outputs/apk/debug/phone-app-debug.apk
glasses-app/build/outputs/apk/debug/glasses-app-debug.apk
```

Install JSOS Core on the phone with your normal Android workflow, then install
the JSOS HUD APK on the glasses through Hi Rokid / APK Manager.

Do not publish debug or release APKs built with real local Rokid, OpenClaw,
OpenAI, ElevenLabs, or signing credentials.

## Generated Assets Must Not Be Committed

APK outputs are local build artifacts. Public repositories should contain source
code and public documentation, not APKs built with local credentials or signing
configuration.

`.gitignore` still protects the old generated asset path:

```text
phone-app/src/main/assets/glasses-app-release.apk
```

That entry is kept as a safety net for older local worktrees.

## Related Code

- `phone-app/build.gradle.kts`
- `phone-app/src/main/java/com/jsos/phone/ui/settings/SoftwareUpdateSection.kt`
- `phone-app/src/main/java/com/jsos/phone/ui/screens/MainScreen.kt`
- `glasses-app/build.gradle.kts`

## External Reference

- [Rokid-APKs by Anezium](https://github.com/Anezium/Rokid-APKs)
