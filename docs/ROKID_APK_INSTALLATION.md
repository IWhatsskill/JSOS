# JSOS HUD APK Installation Notes

This document describes the public JSOS HUD installation path.

## Current Status

JSOS Core and JSOS HUD are treated as separate APKs:

1. Build the phone app.
2. Build the glasses app.
3. Install JSOS Core on the phone.
4. Install JSOS HUD on the glasses through JSOS Core's HUD Deployment section, or manually through Hi Rokid / APK Manager.

JSOS Core no longer bundles the glasses APK into `phone-app/src/main/assets/`.
The integrated JSOS Core deployment flow requires Hi Rokid to be installed on the phone and already connected to the glasses. JSOS Core selects the JSOS HUD APK and hands installation to Hi Rokid / CXR-L.

Manual Hi Rokid / APK Manager installation remains the fallback path when the integrated deployment flow is unavailable or unstable on a device.

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
the JSOS HUD APK on the glasses from JSOS Core or through Hi Rokid / APK
Manager.

## JSOS Core HUD Deployment Flow

The JSOS Core HUD Deployment section can drive the install without opening a
separate APK Manager app manually.

Requirements:

- Hi Rokid installed on the phone.
- Rokid glasses already connected inside Hi Rokid.
- Phone Wi-Fi enabled, because the Hi Rokid / CXR-L path uses the glasses link.
- A locally built JSOS HUD APK selected from JSOS Core.
- Hi Rokid authorization approved when JSOS Core asks for it.
- Current Global Hi Rokid builds expect a normal density-specific PNG launcher
  icon and require JSOS Core to identify its caller package during the CXR-L
  service bind. The JSOS Core source includes that compatibility path.

Flow:

1. Open JSOS Core.
2. Go to the HUD Deployment section.
3. Select the locally built JSOS HUD APK.
4. Authorize Hi Rokid if needed.
5. Tap `Install via Hi Rokid`.

JSOS Core resets the CXR-L link before each install attempt, waits for both the
CXR-L service and glasses Bluetooth callbacks, waits briefly for a stable link,
then starts the upload/install handoff.

If the link drops or the wait times out, JSOS Core should stop the attempt and
show a retry-friendly status message. Open Hi Rokid, confirm the glasses are
connected, then retry the same selected APK.

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
- `phone-app/libs/README.md`
- `phone-app/libs/client-l-1.0.1-jsos-stripped.aar`
- `phone-app/src/main/java/com/jsos/phone/deployment/HiRokidHudDeploymentManager.kt`
- `phone-app/src/main/java/com/jsos/phone/ui/settings/SoftwareUpdateSection.kt`
- `phone-app/src/main/java/com/jsos/phone/ui/screens/MainScreen.kt`
- `glasses-app/build.gradle.kts`

## External Reference

- [Rokid-APKs by Anezium](https://github.com/Anezium/Rokid-APKs)
- [OverlayRec by Anezium](https://github.com/Anezium/OverlayRec)
