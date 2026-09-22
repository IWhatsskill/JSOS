# Rokid Integration Notes

This document describes how JSOS currently uses the Rokid glasses platform. It is not official Rokid SDK documentation. For authoritative SDK terms, APIs, and device documentation, use Rokid's own developer resources.

## Current Project Dependencies

JSOS currently uses the Rokid CXR split-app model:

| Module | Role | Dependency |
| --- | --- | --- |
| `phone-app` | JSOS Core, phone-side bridge | `com.rokid.cxr:client-m:1.2.1` |
| `phone-app` | Hi Rokid / CXR-L compatibility path | `client-l-1.0.1-jsos-stripped.aar`, derived from `com.rokid.cxr:client-l:1.0.1` |
| `glasses-app` | JSOS HUD, glasses-side bridge | `com.rokid.cxr:cxr-service-bridge:1.0` |

Both apps currently use `minSdk = 28`.

### Version baseline

Verified on 2026-08-29, the versions above are the versions actually pinned by
the current JSOS Public source.

Rokid's Maven metadata currently lists `client-m:1.2.2` as the latest release.
JSOS intentionally remains on its existing, hardware-proven `client-m:1.2.1`
baseline for now. The newer version has an open Wi-Fi Direct regression report
for the tested Pixel 9 Pro and Rokid Glasses combination. An update therefore
requires a separate isolated build and device proof; availability alone is not
treated as compatibility proof.

The files under `docs/rokid-sdk/` and `docs/rokid-sdk-zh/` are vendor reference
snapshots. Version strings in their import examples are not JSOS dependency
declarations. The Gradle files and this table are the current project baseline.

## Runtime Credentials

JSOS Core does not compile Rokid CXR credentials into the APK. The phone app asks for the Rokid access key and client secret at runtime in the HUD/Rokid settings area, stores them in encrypted app storage, and uses them for Rokid CXR pairing, SN verification, and HUD deployment.

Do not commit or publish real Rokid credentials, signing files, private config files, or APKs that include private user data.

## How JSOS Uses Rokid

JSOS Core initializes and manages the phone-side Rokid CXR-M connection through:

- `phone-app/src/main/java/com/jsos/phone/glasses/RokidSdkManager.kt`
- `phone-app/src/main/java/com/jsos/phone/glasses/GlassesConnectionManager.kt`

Current responsibilities include:

- BLE discovery and connection to Rokid glasses.
- Rokid account / SN verification flow using locally stored runtime credentials.
- CXR message transport from phone to glasses.
- Receiving AI key / scene events from the glasses.
- Triggering glasses photo capture through the phone-side SDK path.
- Setting audio routing for TTS playback where supported.
- Wake / display timeout handling from the phone side.
- Redacted logging for device identifiers such as MAC, SN, socket UUID, account, and device name.

JSOS HUD uses the glasses-side bridge through:

- `glasses-app/src/main/java/com/jsos/glasses/service/PhoneConnectionService.kt`

Current responsibilities include:

- Starting `CXRServiceBridge`.
- Monitoring phone connection status.
- Receiving JSON messages from JSOS Core.
- Sending HUD actions, staged input, session changes, photo requests, and control messages back to the phone.

Debug builds also include WebSocket-style debug paths for emulator/development testing. The production glasses path is the Rokid CXR bridge.

## UI And Display Notes

The JSOS HUD targets a high-contrast wearable display:

- Black background.
- Bright green primary HUD text.
- Minimal fills to reduce ghosting and low-brightness artifacts.
- Safe-zone layout values tuned for Rokid glasses.
- Larger text and simple panels for touchpad navigation.

The HUD supports OPTIONS, COMMANDS, SESSIONS, staged voice input, photo capture requests, TTS state, and Full/Mid/Bottom display modes.

## Public Safety Notes

Public docs, screenshots, and logs should avoid exposing:

- Rokid account values.
- Device names.
- MAC addresses.
- Serial numbers / SN values.
- Socket UUIDs.
- API keys, tokens, signing files, or local build properties.

Runtime pairing, gateway, and Rokid credential values are stored locally by JSOS Core; sensitive values use Android Keystore-backed encrypted app storage.

## Useful External Resources

- [Rokid GitHub](https://github.com/rokid)
- [RokidGlass GitHub](https://github.com/RokidGlass)
- [Rokid Maven repository](https://maven.rokid.com/repository/maven-public/)
- [Rokid `client-m` Maven metadata](https://maven.rokid.com/repository/maven-public/com/rokid/cxr/client-m/maven-metadata.xml)
- [Open `client-m:1.2.2` Wi-Fi Direct regression report](https://github.com/rokid/community/issues/18)
- [Rokid developer portal](https://ar.rokid.com)
- [Rokid-APKs by Anezium](https://github.com/Anezium/Rokid-APKs)
