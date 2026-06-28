# JSOS

**Spatial Operating System for Smart Glasses**

JSOS is an Android-based smart-glasses interface for **Rokid glasses** and a local or private **OpenClaw Gateway**. It combines a phone-side control deck with a lightweight glasses HUD for sessions, voice input, camera handoff, direct R08 ring control, and optional private bridge workflows.

JSOS started as an AGPL-3.0 fork of the upstream Clawsses project and has been substantially reworked into a JSOS-specific development-preview project. It is not presented as a finished consumer product.

<p align="center">
  <a href="https://github.com/IWhatsskill/JSOS/releases"><strong>Download preview APKs</strong></a>
  &nbsp;|&nbsp;
  <a href="docs/videos/JSOS-showcase.mp4"><strong>Watch showcase video</strong></a>
  &nbsp;|&nbsp;
  <a href="docs/SCREENSHOTS.md"><strong>Screenshots</strong></a>
  &nbsp;|&nbsp;
  <a href="#quick-start"><strong>Quick start</strong></a>
  &nbsp;|&nbsp;
  <a href="#docs-map"><strong>Docs map</strong></a>
</p>

<p align="center">
  <img src="docs/images/jsos-social-preview.png" alt="JSOS smart-glasses HUD preview" width="100%">
</p>

## What JSOS Includes

| Part | Runs on | Purpose |
| --- | --- | --- |
| **JSOS Core** | Android phone | Connects to OpenClaw, manages sessions, stores local runtime settings, handles voice input, TTS settings, camera handoff, Rokid pairing, HUD deployment, glasses brightness, and the optional Admin Codex bridge client. |
| **JSOS HUD** | Rokid glasses | Renders the lightweight glasses HUD, receives streamed chat updates, handles touchpad and configurable direct R08 ring gestures, stages voice input, displays sessions, requests photo capture, and triggers Rokid AI/photo/AR picture/recording scenes. |
| **JSOS Watch** | Wear OS watch | Optional companion app for controlling JSOS Core, mirroring status/chat, switching sessions/models, triggering Codex resume, and testing watch mic/TTS output flows through the phone. |
| **Admin Codex bridge** | User-managed private host | Optional self-hosted bridge for showing Codex-style output inside JSOS Core and JSOS HUD. The bridge service, credentials, and server setup are not included in this repository. |

## Preview Boundaries

- JSOS is a **development preview**, not a finished consumer product.
- Rokid behavior depends on the proprietary Rokid CXR SDK, target device firmware, and Hi Rokid availability.
- OpenClaw Live Talk and the Admin Codex bridge path are experimental and should be tested against the target gateway/bridge version.
- Runtime OpenClaw, Rokid, OpenAI, ElevenLabs, and bridge credentials are configured locally and are not shipped in this repository.
- JSOS Watch is a companion surface only. It does not store credentials and does not connect directly to OpenClaw.
- Release signing keys, local signing properties, and built APKs must remain private.

## Visual Overview

The full public-safe screenshot set is listed in [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md). The main README keeps only a short preview so the GitHub project page stays readable.

### JSOS Core

<p align="center">
  <img src="docs/images/jsos-core-home.jpeg" alt="JSOS Core home dashboard" width="23%">
  <img src="docs/images/jsos-core-session-chat.jpeg" alt="JSOS Core session chat view" width="23%">
  <img src="docs/images/jsos-core-voice.jpeg" alt="JSOS Core voice settings" width="23%">
  <img src="docs/images/jsos-core-diagnostics.jpeg" alt="JSOS Core diagnostics dashboard" width="23%">
</p>

### JSOS HUD

<p align="center">
  <img src="docs/images/jsos-hud-home.jpeg" alt="JSOS HUD home view captured through Rokid glasses" width="23%">
  <img src="docs/images/jsos-hud-bottom-menu.jpeg" alt="JSOS HUD bottom menu captured through Rokid glasses" width="23%">
  <img src="docs/images/jsos-hud-ar-tools.jpeg" alt="JSOS HUD AR tools panel captured through Rokid glasses" width="23%">
  <img src="docs/images/jsos-codex-hud-home.jpeg" alt="JSOS Admin Codex HUD captured through Rokid glasses" width="23%">
</p>

## Quick Start

1. Download the latest JSOS Core and JSOS HUD APKs from [GitHub Releases](https://github.com/IWhatsskill/JSOS/releases).
2. Allow Android to install APKs from the app used to open the downloaded files.
3. Install JSOS Core on the phone.
4. Open JSOS Core and configure OpenClaw Gateway host, port, token, and required runtime credentials.
5. Pair the Rokid glasses through Hi Rokid and connect them from JSOS Core.
6. Install the separate JSOS HUD APK on the glasses through JSOS Core HUD Deployment, or manually through Hi Rokid / APK Manager.
7. Launch JSOS HUD on the glasses and connect to the phone-side JSOS Core session flow.

### Upgrade Note

This release was signed with a new public JSOS signing certificate.

If you already have an older JSOS version installed, Android may refuse to install these APKs as an update. In that case, uninstall the old JSOS Core and JSOS HUD apps first, then install the new APKs from this release.

## Architecture

| Component | Link | Responsibilities |
| --- | --- | --- |
| OpenClaw Gateway | WebSocket to JSOS Core | AI sessions, chat streaming, tool execution, pairing/device approval. |
| JSOS Core | WebSocket to OpenClaw; Bluetooth CXR to JSOS HUD | Phone-side bridge, voice input, TTS playback, wake management, runtime setup, HUD deployment. |
| JSOS HUD | Bluetooth CXR messages from JSOS Core; local R08 BLE/HID/accessibility; local Rokid scene commands | Glasses HUD, gestures, session output, camera requests, AR picture/record triggers, session picker. |
| JSOS Watch | Wear OS Data Layer to JSOS Core | Companion controls and status mirror. Core remains the source of truth for credentials, sessions, models, gateway state, HUD state, and Codex bridge actions. |
| Optional Admin Codex bridge | WebSocket from JSOS Core to a user-managed private bridge | Experimental Codex-style output in JSOS HUD/Core, separate from OpenClaw agents. |

### Wear OS Companion Preview

`watch-app/` is a small Wear OS companion for JSOS Core. It can show Core/HUD/Gateway status, mirror the current chat snapshot, switch sessions and models, send `/reset` or `/clear`, start/stop voice flows, trigger compact Codex resume actions, and test watch microphone input or TTS playback routed back to the watch.

The watch talks to the phone through the Wear OS Data Layer. JSOS Core on the phone keeps the OpenClaw connection, runtime credentials, sessions, model list, gateway state, HUD state, and optional Codex bridge state. No OpenClaw tokens, API keys, signing files, or private host configuration belong on the watch.

## Build From Source

Requirements:

- Android Studio or a working Android Gradle environment with JDK 17.
- Android SDK and platform tools.
- Rokid glasses for real-device testing.
- Rokid CXR SDK access and credentials.
- A running OpenClaw Gateway reachable from the phone.

Debug builds are the normal local development path:

```bash
./gradlew :phone-app:assembleDebug :glasses-app:assembleDebug :watch-app:assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :phone-app:assembleDebug :glasses-app:assembleDebug :watch-app:assembleDebug
```

Expected debug outputs:

```text
phone-app/build/outputs/apk/debug/phone-app-debug.apk
glasses-app/build/outputs/apk/debug/glasses-app-debug.apk
watch-app/build/outputs/apk/debug/watch-app-debug.apk
```

Release signing is local-only. Keep `jsos-release.properties`, keystores such as `*.jks`, credentials, and generated APKs private. Do not commit them.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `phone-app/` | JSOS Core Android phone app. Bridges OpenClaw, Rokid CXR, voice, TTS, sessions, camera handoff, and HUD deployment. |
| `glasses-app/` | JSOS HUD Android glasses app. Renders the HUD, handles gestures, direct R08 ring control, sessions, staged input, voice state, camera requests, and Rokid AR picture/record triggers. |
| `watch-app/` | Optional Wear OS companion app for JSOS Core controls, status, chat snapshot, Codex resume, watch mic, and watch TTS playback preview. |
| `shared/` | Shared JSON protocol models used between phone, glasses, watch, and OpenClaw-facing code. |
| `docs/` | Public notes, setup details, Rokid references, screenshots, and long-form project documentation. |
| `gradle/` | Gradle wrapper files. |
| `LICENSE` | GNU AGPL license text. |
| `COPYRIGHT` | Fork, attribution, modification, and third-party notices. |

## Docs Map

| Document | What it covers |
| --- | --- |
| [docs/INSTALL.md](docs/INSTALL.md) | Requirements, local configuration, OpenClaw setup, build commands, release signing, and connect flow. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Phone app, glasses app, shared protocol, OpenClaw gateway protocol, phone-glasses messages, and emulator testing. |
| [docs/HUD.md](docs/HUD.md) | HUD display model, controls, voice modes, camera/AR tools, direct R08 ring mappings, wake, and TTS behavior. |
| [docs/CODEX-BRIDGE.md](docs/CODEX-BRIDGE.md) | Public-safe notes for the optional private Admin Codex bridge client. |
| [docs/SECURITY.md](docs/SECURITY.md) | Credentials, signing files, network exposure, logs, screenshots, and release artifact safety. |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Connection, pairing, install, voice, TTS, wake, and emulator debug troubleshooting. |
| [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md) | Public-safe screenshot list and visual asset notes. |
| [docs/ROKID.md](docs/ROKID.md) | Rokid integration notes. |
| [docs/ROKID_APK_INSTALLATION.md](docs/ROKID_APK_INSTALLATION.md) | Hi Rokid / APK installation notes. |
| [docs/rokid-sdk/README.md](docs/rokid-sdk/README.md) | Public Rokid phone-side SDK reference notes included for development context. |
| [docs/rokid-sdk-glasses/README.md](docs/rokid-sdk-glasses/README.md) | Public Rokid glasses-side SDK reference notes included for development context. |
| [COPYRIGHT](COPYRIGHT) | Fork attribution, modification notices, and third-party notices. |
| [LICENSE](LICENSE) | GNU AGPL-3.0 license text. |

## Security Notes

Do not publish:

- `local.properties`
- `jsos-release.properties`
- `signing.properties`
- `*.jks`
- `.env` or `.env.*`
- private keys, tokens, API keys, access keys, client secrets, or signing passwords
- built APKs from private local configurations
- logs, screenshots, or APKs containing transcripts, session keys, API keys, device identifiers, or account data

Use trusted private-network access for local gateways and private bridges. Do not expose an OpenClaw Gateway or experimental Admin Codex bridge directly to the public internet.

## Attribution And License

JSOS is based on the upstream [Clawsses](https://github.com/dweddepohl/clawsses) Android glasses project.

Original upstream copyright remains with Pohlster BV and the original contributors. JSOS modifications are by Whatsskill, 2026.

This project is distributed under the GNU Affero General Public License, version 3. See [LICENSE](LICENSE) and [COPYRIGHT](COPYRIGHT).

The Rokid CXR SDK is proprietary software from Rokid and is licensed separately by Rokid. It is not covered by the AGPL license of this repository and must be obtained under Rokid's own terms. JSOS includes a small CXR-L compatibility AAR derived from Rokid's public Maven `client-l:1.0.1` artifact for the Hi Rokid deployment flow; see [phone-app/libs/README.md](phone-app/libs/README.md).

## Thanks

Thanks to:

- [dweddepohl](https://github.com/dweddepohl)
- [OpenClaw](https://github.com/openclaw/openclaw)
- [Anezium](https://github.com/Anezium)
- [Rokid](https://github.com/rokid)
