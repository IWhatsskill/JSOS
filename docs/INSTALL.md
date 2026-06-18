# Install And Build

This document collects the install, local configuration, build, and signing notes that do not need to live on the front page.

## Requirements

- Android Studio or a working Android Gradle environment with JDK 17.
- Android SDK and platform tools.
- Rokid glasses for real-device testing.
- Rokid CXR SDK access and credentials.
- A running OpenClaw Gateway reachable from the phone.
- Optional: OpenAI API key for OpenAI Realtime speech-to-text.
- Optional: ElevenLabs API key and voice ID/name for TTS.
- Optional: a trusted private network path for remote gateway or private bridge access.

## Install From GitHub Releases

GitHub Releases are the normal installation path for preview users.

Each release should provide:

- `JSOS-Core-v<version>.apk` for the Android phone.
- `JSOS-HUD-v<version>.apk` for the Rokid glasses.

The current public preview APKs are signed with a new public JSOS signing certificate. If Android refuses to install them as updates over an older JSOS version, uninstall the old JSOS Core and JSOS HUD apps first, then install the new APKs from the release.

JSOS never ships private OpenClaw tokens, Rokid credentials, OpenAI keys, ElevenLabs keys, signing keys, or Admin Codex bridge credentials. These values are entered locally inside JSOS Core.

## Local Configuration

JSOS Core stores runtime settings locally on the phone. Rokid CXR credentials are configured inside the app and are not compiled into the APK.

Configure these values in JSOS Core:

- OpenClaw Gateway host, port, and token.
- Rokid CXR access key and client secret.
- Optional OpenAI API key for Realtime speech-to-text.
- Optional ElevenLabs API key and voice for TTS.
- Optional private Admin Codex bridge host.

Use `local.properties` only for Android SDK paths or local signing fallback values. Do not commit or publish `local.properties`.

## OpenClaw Gateway Setup

JSOS Core connects to an OpenClaw Gateway over WebSocket. In JSOS Core, the gateway host may be entered as a full `ws://` or `wss://` URL, or as a host plus port.

Bare host entries stay compatible with local OpenClaw setups and resolve to `ws://host:port`. Enter a full `wss://` URL when your gateway supports TLS.

Typical local gateway token setup:

```bash
openclaw config set gateway.auth.token <your-token>
```

For LAN testing from a phone, the gateway must listen on an address reachable from that phone:

```bash
openclaw config set gateway.host 0.0.0.0
openclaw gateway restart
```

For remote access, use a trusted private network instead of exposing the gateway directly to the public internet. `ws://` is cleartext at the app layer and should only be used over localhost or trusted private-network links.

On first connection, OpenClaw may require device approval:

```bash
openclaw devices list
openclaw devices approve <requestId>
```

The first connection attempt may fail with a pairing or approval error until the gateway device request is approved. After approval, reconnect from JSOS Core.

## Build From Source

JSOS Core and JSOS HUD are built and installed as separate Android apps:

- JSOS Core is installed on the phone.
- JSOS HUD is installed on the glasses through JSOS Core's HUD Deployment section or manually through Hi Rokid / APK Manager.

Debug builds are the normal local development path.

From the project root:

```bash
./gradlew :phone-app:assembleDebug :glasses-app:assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :phone-app:assembleDebug :glasses-app:assembleDebug
```

Expected debug outputs:

```text
phone-app/build/outputs/apk/debug/phone-app-debug.apk
glasses-app/build/outputs/apk/debug/glasses-app-debug.apk
```

Install JSOS Core on the phone:

```bash
adb install phone-app/build/outputs/apk/debug/phone-app-debug.apk
```

Install JSOS HUD from JSOS Core's HUD Deployment section, or manually through Hi Rokid / APK Manager. The integrated deployment flow depends on Hi Rokid being installed on the phone and already connected to the glasses.

Do not publish private debug APKs built with local test data.

## Release Builds And Signing

Release signing is local-only.

Users can build their own local release APKs by creating their own Android signing key and a local `jsos-release.properties` file. The Gradle files can read signing values from ignored local signing properties or from the `JSOS_SIGNING_PROPERTIES` environment variable.

The local signing properties must provide:

```properties
storeFile=path/to/private-keystore.jks
storePassword=<private>
keyAlias=<private>
keyPassword=<private>
```

Keep `jsos-release.properties`, keystores such as `*.jks`, credentials, and generated APKs private. They are ignored by Git and must not be committed.

Do not assume release builds are publishable from a fresh public checkout. Build and signing setup must be supplied locally by the person producing a release.

For a fresh JSOS install, a new signing key is fine. If a device already has an app installed with the same `applicationId` (`com.jsos.phone` or `com.jsos.glasses`) but signed with a different key, Android will reject the update. In that case, uninstall the old app first or rebuild with the same old signing key.

## Connect

1. Start the OpenClaw Gateway and make sure the phone can reach it on the same LAN or private VPN.
2. Open JSOS Core and configure the gateway host, port, and token in the System Link area.
3. Connect to OpenClaw. If the gateway reports pairing required, approve the pending device on the gateway and reconnect.
4. Put the Rokid glasses into their normal Bluetooth pairing mode, then scan and connect from the JSOS Core HUD Link area.
5. Install JSOS HUD on the glasses through JSOS Core's HUD Deployment section, or manually through Hi Rokid / APK Manager.
6. Launch JSOS HUD on the glasses. The HUD should show connection and session status once the phone bridge is connected.
