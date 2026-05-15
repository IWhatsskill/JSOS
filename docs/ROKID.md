# Rokid Integration Notes

This document describes how JSOS currently uses the Rokid glasses platform. It is not official Rokid SDK documentation. For authoritative SDK terms, APIs, and device documentation, use Rokid's own developer resources.

## Current Project Dependencies

JSOS currently uses the Rokid CXR split-app model:

| Module | Role | Dependency |
| --- | --- | --- |
| `phone-app` | JSOS Core, phone-side bridge | `com.rokid.cxr:client-m:1.2.1` |
| `glasses-app` | JSOS HUD, glasses-side bridge | `com.rokid.cxr:cxr-service-bridge:1.0` |

Both apps currently use `minSdk = 28`.

## Local Credentials

The phone app reads Rokid CXR credentials from the local, ignored `local.properties` file:

```properties
rokid.clientSecret=your-client-secret
rokid.accessKey=your-access-key
```

These values are injected into the phone app as `BuildConfig` fields for local builds. Do not commit or publish real Rokid credentials, signing files, or APKs built with private credentials.

## How JSOS Uses Rokid

JSOS Core initializes and manages the phone-side Rokid CXR-M connection through:

- `phone-app/src/main/java/com/jsos/phone/glasses/RokidSdkManager.kt`
- `phone-app/src/main/java/com/jsos/phone/glasses/GlassesConnectionManager.kt`

Current responsibilities include:

- BLE discovery and connection to Rokid glasses.
- Rokid account / SN verification flow using local credentials.
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

Runtime pairing and gateway values are currently stored in local app storage. A future hardening pass can move sensitive runtime values to encrypted storage.

## Useful External Resources

- [Rokid GitHub](https://github.com/rokid)
- [RokidGlass GitHub](https://github.com/RokidGlass)
- [Rokid Maven repository](https://maven.rokid.com/repository/maven-public/)
- [Rokid developer portal](https://ar.rokid.com)
- [Rokid-APKs by Anezium](https://github.com/Anezium/Rokid-APKs)
