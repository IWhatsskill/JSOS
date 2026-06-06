# JSOS

**Spatial Operating System for Smart Glasses**

**JSOS** is an Android-based smart-glasses interface for a local or private **OpenClaw Gateway** and **Rokid glasses**.

JSOS is split into two Android apps and one optional private bridge path:

| Part | Runs on | Purpose |
| --- | --- | --- |
| **JSOS Core** | Android phone | Connects to OpenClaw, manages sessions, stores local runtime settings, handles voice input, TTS settings, camera handoff, Rokid pairing, HUD deployment, glasses brightness, legacy phone-side media-ring input, and the experimental private Admin Codex bridge client. |
| **JSOS HUD** | Rokid glasses | Renders the lightweight glasses HUD, receives streamed chat updates, handles touchpad and direct R08 ring gestures, stages voice input, displays sessions, requests photo capture, triggers Rokid AR picture/recording scenes, and includes an experimental Admin Codex terminal view. |
| **Admin Codex bridge** | User-managed private host | Optional self-hosted bridge for showing Codex CLI-style output inside JSOS Core and JSOS HUD. The bridge service, credentials, and server setup are not included in this repository. |

JSOS started as a fork of the upstream Clawsses project and has been substantially reworked into a separate development-preview project. It is being prepared as a public fork base and is not presented as a finished consumer product.

<p align="center">
  <img src="docs/images/jsos-social-preview.png" alt="JSOS smart-glasses HUD preview" width="100%">
</p>

## Showcase

The repository includes a short JSOS showcase video for a quick visual overview:

- **JSOS HUD** running on Rokid glasses with the green monochrome interface.
- **JSOS Core** controlling gateway, sessions, voice, HUD deployment, and Codex bridge input.
- **Admin Codex bridge** output rendered directly inside the glasses HUD.
- **Direct R08 ring control** on the glasses for quick HUD, launcher, media, camera, and system-panel navigation.

<p align="center">
  <a href="docs/videos/JSOS-showcase.mp4"><strong>Download / view the JSOS showcase video</strong></a>
</p>

## Visual Overview

These public-safe visuals show the current JSOS interface direction. They are documentation examples, not official APK release assets.

### JSOS Core

JSOS Core is the phone-side control deck for gateway connection, session routing, voice, HUD deployment, brightness, legacy phone-side media-ring input, and the private Admin Codex bridge client.

#### Home, Chat, Voice, Diagnostics

<p align="center">
  <img src="docs/images/jsos-core-home.jpeg" alt="JSOS Core home dashboard" width="22%">
  <img src="docs/images/jsos-core-session-chat.jpeg" alt="JSOS Core session chat view" width="22%">
  <img src="docs/images/jsos-core-voice.jpeg" alt="JSOS Core voice settings and live talk mode" width="22%">
  <img src="docs/images/jsos-core-diagnostics.jpeg" alt="JSOS Core diagnostics dashboard" width="22%">
</p>

#### Config

System Link, HUD Link, HUD Deployment, Voice Matrix, and Response Voice are kept as focused setup panels instead of buried Android settings screens.

<p align="center">
  <img src="docs/images/jsos-core-system-link.jpeg" alt="JSOS Core OpenClaw system link settings" width="19%">
  <img src="docs/images/jsos-core-hud-link.png" alt="JSOS Core HUD link settings with redacted device name" width="19%">
  <img src="docs/images/jsos-core-hud-deployment.jpeg" alt="JSOS Core HUD deployment settings" width="19%">
  <img src="docs/images/jsos-core-voice-matrix.jpeg" alt="JSOS Core OpenAI voice matrix settings with redacted API key" width="19%">
  <img src="docs/images/jsos-core-response-voice.jpeg" alt="JSOS Core response voice settings with redacted API key" width="19%">
</p>

### JSOS HUD

JSOS HUD is the glasses-side interface: green monochrome, touchpad-first, safe-zone aware, and designed to stay readable over the real world.

<p align="center">
  <img src="docs/images/jsos-hud-home.jpeg" alt="JSOS HUD home view captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-bottom-menu.jpeg" alt="JSOS HUD bottom menu captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-options-live.jpeg" alt="JSOS HUD options panel captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-ar-tools.jpeg" alt="JSOS HUD AR tools panel captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-codex-hud-home.jpeg" alt="JSOS Admin Codex HUD captured through Rokid glasses" width="19%">
</p>

<p align="center">
  <img src="docs/images/jsos-hud-commands-live.jpeg" alt="JSOS HUD commands panel captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-commands-models.jpeg" alt="JSOS HUD command model selection captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-models.jpeg" alt="JSOS HUD models panel captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-think.jpeg" alt="JSOS HUD think-mode panel captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-launcher.jpeg" alt="JSOS HUD launcher overlay captured through Rokid glasses" width="19%">
</p>

## Current Status

Included in this development preview:

- Android multi-module project with `phone-app`, `glasses-app`, and `shared` modules.
- JSOS Core and JSOS HUD app labels, package namespaces, and launcher branding use JSOS naming.
- Debug builds are the supported local development path.
- OpenClaw Gateway integration, Rokid CXR transport, sessions, streaming chat, voice input, Core/Glasses Live Talk routing, optional ElevenLabs TTS, wake signaling, glasses brightness control, direct R08 ring control on the glasses, legacy phone-side media-ring HUD navigation, Hi Rokid / CXR-L HUD deployment, Rokid AR picture/recording triggers, and an experimental private Admin Codex bridge client are present in this source tree.
- Selected screenshots and visual assets are referenced for public documentation. They should remain neutral and redacted before publication.

Development-preview boundaries:

- Rokid device behavior depends on the proprietary Rokid CXR SDK and device firmware.
- OpenClaw Live Talk support is present in the JSOS codebase, but should be treated as experimental unless tested against the target OpenClaw Gateway version and audio route.
- The Admin Codex HUD/Core path is experimental. It expects a user-managed private WebSocket bridge to a separate Codex CLI environment and does not include a hosted service, Codex credentials, or server setup.
- Official preview APKs may be published through GitHub Releases. Release signing keys remain private and are never stored in the repository.
- Runtime OpenClaw, OpenAI, ElevenLabs, and device-identity secrets are stored in Android Keystore-backed encrypted app storage.

## Project Lineage And JSOS Changes

JSOS started as an AGPL-3.0 fork of Clawsses by Pohlster BV / dweddepohl. This repository keeps the original attribution and license requirements while developing a JSOS-specific smart-glasses workflow.

JSOS keeps the core idea of a phone-to-glasses Android bridge and expands it into a development-preview spatial interface built around OpenClaw, Rokid glasses, voice input, sessions, and a dedicated HUD experience.

Main JSOS changes include:

- Renamed and rebranded app identity for JSOS Core and JSOS HUD.
- Neutral JSOS launcher icons, README imagery, and public-safe branding.
- Updated Android package namespaces under `com.jsos.phone` and `com.jsos.glasses`.
- Complete visual redesign of JSOS Core for everyday use, with a modern control-deck home screen, diagnostics-oriented panels, redesigned settings screens, HUD deployment area, and voice-mode controls.
- Complete visual redesign of JSOS HUD for the glasses display, with safe-zone placement, green monochrome rendering, Full/Mid/Bottom display modes, top-bar voice state, wave/live indicators, and revised chat presentation.
- Reading-first HUD behavior where the bottom menu stays hidden while content is being read and appears only when the user navigates to actions.
- HUD chat behavior focused on JSOS/assistant output, while user input remains available in JSOS Core and session history.
- Suppression of unwanted Rokid AI auto-message overlays while JSOS HUD handles the interaction.
- Restyled existing OPTIONS, COMMANDS, and SESSIONS HUD panels into the JSOS green monochrome HUD design, with low-brightness-friendly outlines, compact readable bottom-menu buttons, direct bottom-bar Codex/slash access, and grouped `AR TOOLS` / `DISPLAY` submenus.
- Staged voice input on the glasses with `Send Ask` and `Send Auto` modes.
- Direct R08 ring control on the glasses: JSOS HUD includes `MORE -> RING` setup, BLE pair/forget/reconnect support, a JSOS Accessibility Service for global R08 HID/media-key events, and mappings for tap, double tap, swipe, launcher/system navigation, music control, and camera capture.
- Legacy phone-side Bluetooth media-ring path: JSOS Core can still capture phone-side media button events and forward them as HUD gestures, but this is not the current R08 primary path.
- Session picker presentation updates, current-session markers, unread indicators, and session/chat forwarding behavior.
- OpenClaw Gateway protocol negotiation used by JSOS, currently advertising a v4-v5 client range and showing the negotiated gateway protocol in JSOS Core.
- OpenAI Realtime voice input path with Android SpeechRecognizer fallback.
- Core Agent Wake mode for phone-side continuous OpenAI Realtime transcription, leading-agent-name session routing, follow-up messages in the active session, and alias handling for configured visible JSOS sessions.
- Updated the existing ElevenLabs TTS, Rokid CXR transport, and HUD camera request flows for the JSOS codebase, current dependencies, JSOS UI, and public-safe runtime logging.
- Integrated a Hi Rokid / CXR-L HUD deployment flow in JSOS Core so the phone app can select a JSOS HUD APK and hand installation to Hi Rokid when Hi Rokid is installed and already connected to the glasses.
- Added a JSOS-built `client-l:1.0.1` compatibility artifact derived from Rokid's public Maven artifact, stripped only of duplicate classes/native libraries already supplied by `client-m:1.2.1`.
- Hardened the Hi Rokid / CXR-L deployment flow with link reset, Bluetooth/CXR readiness timeouts, stable-link delay before upload, and retry-friendly failure messages.
- Added JSOS HUD `AR PIC` and `AR REC` options that trigger Rokid AR picture / mixed-recording scene commands from the glasses app.
- Added an experimental Admin Codex HUD/Core bridge client for private bridge setups, with JSOS-style terminal panels, link/send/stop/clear controls, staged photo input, and scrollable glasses output.
- Chunked phone-to-glasses message transport for larger JSON payloads.
- Wake acknowledgments and status messages between phone and HUD.
- Public-readiness cleanup: neutral assets, README rewrite, safer `.gitignore`, redacted sensitive logs, local-only signing, and clearer security notes.

## Quick Start

1. Download the latest JSOS Core and JSOS HUD APKs from [GitHub Releases](https://github.com/IWhatsskill/JSOS/releases).
2. Allow Android to install APKs from the app you use to open the downloaded files.
3. Install JSOS Core on the phone.
4. Open JSOS Core and configure:
   - OpenClaw Gateway host/port/token.
   - Rokid CXR access key and client secret in the HUD/Rokid settings area.
   - Optional OpenAI, ElevenLabs, and private Admin Codex bridge settings.
5. Approve the phone device on the OpenClaw Gateway if pairing is required.
6. Pair the Rokid glasses in Hi Rokid and JSOS Core.
7. Install the separate JSOS HUD APK on the glasses either from JSOS Core's HUD Deployment section, which uses Hi Rokid / CXR-L, or manually through Hi Rokid / APK Manager.

## Architecture

| Component | Link | Responsibilities |
| --- | --- | --- |
| OpenClaw Gateway | WebSocket to JSOS Core | AI sessions, chat streaming, tool execution |
| JSOS Core / Phone App | WebSocket to OpenClaw; Bluetooth CXR to JSOS HUD | Bridge + voice, TTS playback, wake management |
| JSOS HUD / Glasses App | Bluetooth CXR messages from JSOS Core; local R08 BLE/HID/accessibility; local Rokid scene commands | HUD + gestures, direct R08 control, camera capture, AR picture/record triggers, session picker |
| Optional private Admin Codex bridge | WebSocket from JSOS Core to a user-managed private bridge | Experimental Codex CLI output in JSOS HUD/Core, separate from OpenClaw agents |

## Repository Layout

| Path | Purpose |
| --- | --- |
| `phone-app/` | JSOS Core Android phone app. Bridges OpenClaw, Rokid CXR, voice, TTS, sessions, camera handoff, and HUD deployment. |
| `glasses-app/` | JSOS HUD Android glasses app. Renders the HUD, handles gestures, direct R08 ring control, sessions, staged input, voice state, camera requests, Rokid AR picture/record triggers, and the experimental Codex CLI view. |
| `shared/` | Shared JSON protocol models used between phone, glasses, and OpenClaw-facing code. |
| `docs/` | Public notes and neutral visual assets. Keep screenshots redacted before publication. |
| `gradle/` | Gradle wrapper files. |
| `LICENSE` | GNU AGPL license text. |
| `COPYRIGHT` | Fork, attribution, modification, and third-party notices. |

## Requirements

- Android Studio or a working Android Gradle environment with JDK 17.
- Android SDK and platform tools.
- Rokid glasses for real-device testing.
- Rokid CXR SDK access and credentials.
- A running OpenClaw Gateway reachable from the phone.
- Optional: OpenAI API key for OpenAI Realtime speech-to-text.
- Optional: ElevenLabs API key and voice ID/name for TTS.
- Optional: a trusted private network path for remote access to the gateway.

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

JSOS Core connects to an OpenClaw Gateway over WebSocket. In JSOS Core, the gateway host may be entered as a full `ws://` or `wss://` URL, or as a host plus port. Bare host entries stay compatible with local OpenClaw setups and resolve to `ws://host:port`; enter a full `wss://` URL when your gateway supports TLS.

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

## Install From GitHub Releases

GitHub Releases are the normal installation path for preview users.

Each release should provide:

- `JSOS-Core-v<version>.apk` for the Android phone.
- `JSOS-HUD-v<version>.apk` for the Rokid glasses.

The APKs are signed preview builds. If a device already has a debug/self-built JSOS app installed with the same package name but a different signing key, Android may require uninstalling the old app first.

JSOS never ships private OpenClaw tokens, Rokid credentials, OpenAI keys, ElevenLabs keys, signing keys, or Admin Codex bridge credentials. These values are entered locally inside JSOS Core.

## Build From Source

The repository also supports local development builds for users who want to inspect or modify the source.

JSOS Core and JSOS HUD are built and installed as separate Android apps:

- JSOS Core is installed on the phone.
- JSOS HUD is installed on the glasses through Hi Rokid. JSOS Core can drive this from its HUD Deployment section when Hi Rokid is installed and the glasses are already connected there; manual Hi Rokid / APK Manager installation remains a fallback.

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

Install JSOS HUD as a separate APK on the glasses from JSOS Core's HUD Deployment section, or manually through Hi Rokid / APK Manager. The JSOS Core deployment flow still depends on Hi Rokid being installed on the phone and already connected to the glasses.

Do not publish private debug APKs built with local test data.

## Release Builds And Signing

Release signing is local-only.

Users can build their own local release APKs by creating their own Android
signing key and a local `jsos-release.properties` file. The Gradle files can
read signing values from ignored local signing properties such as
`jsos-release.properties` or from the `JSOS_SIGNING_PROPERTIES` environment
variable.

The local signing properties must provide:

```properties
storeFile=path/to/private-keystore.jks
storePassword=<private>
keyAlias=<private>
keyPassword=<private>
```

Keep `jsos-release.properties`, keystores such as `*.jks`, credentials, and
generated APKs private. They are ignored by Git and must not be committed.

Do not assume release builds are publishable from a fresh public checkout. Build and signing setup must be supplied locally by the person producing a release.

For a fresh JSOS install, a new signing key is fine. If a device already has an
app installed with the same `applicationId` (`com.jsos.phone` or
`com.jsos.glasses`) but signed with a different key, Android will reject the
update. In that case, uninstall the old app first or rebuild with the same old
signing key.

## Connect

1. Start the OpenClaw Gateway and make sure the phone can reach it on the same LAN or private VPN.
2. Open JSOS Core and configure the gateway host, port, and token in the System Link area.
3. Connect to OpenClaw. If the gateway reports pairing required, approve the pending device on the gateway and reconnect.
4. Put the Rokid glasses into their normal Bluetooth pairing mode, then scan and connect from the JSOS Core HUD Link area.
5. Install JSOS HUD on the glasses through JSOS Core's HUD Deployment section, or manually through Hi Rokid / APK Manager.
6. Launch JSOS HUD on the glasses. The HUD should show connection/session status once the phone bridge is connected.

## What Runs Where

### JSOS Core phone app

JSOS Core is responsible for:

- OpenClaw Gateway WebSocket connection.
- Token authentication and Ed25519 device identity.
- Session listing, switching, reset/new-session flow, and chat history loading.
- Streaming chat forwarding to the HUD.
- Rokid CXR phone-side connection and device control.
- HUD brightness preference for connected Rokid glasses.
- Debug WebSocket bridge for emulator-style local testing.
- OpenAI Realtime speech-to-text when configured.
- Core Agent Wake: phone-side continuous Realtime transcription with leading-agent-name routing into the visible JSOS sessions.
- Android SpeechRecognizer fallback.
- Optional ElevenLabs TTS settings and playback path.
- Photo capture handoff from the glasses to the phone/OpenClaw flow.
- HUD deployment flow for selecting a separate JSOS HUD APK and handing installation to Hi Rokid / CXR-L.
- Experimental private Admin Codex bridge client for the Core Codex tab and HUD Codex CLI view.

### JSOS HUD glasses app

JSOS HUD is responsible for:

- Green monochrome HUD rendering for the Rokid display target.
- Touchpad gesture handling.
- Chat stream display and history rendering.
- HUD chat focus on JSOS/assistant output, while user input remains available in JSOS Core and session history.
- Suppression of unwanted Rokid AI auto-message overlays where JSOS HUD is handling the interaction.
- Session picker UI.
- OPTIONS, COMMANDS, SESSIONS, AR TOOLS, and DISPLAY overlays.
- Top-bar voice state display, including wave/live status for active voice modes.
- Reading-first behavior where the bottom menu stays hidden while content is being read and appears only when needed.
- Staged voice input.
- `Send Ask` and `Send Auto` modes.
- Camera request flow with a single staged photo preview and remove action.
- Rokid AR Picture and AR Record scene triggers from the HUD AR TOOLS submenu.
- Wake acknowledgments and TTS toggle messages back to the phone.
- Experimental Admin Codex terminal view for private Admin Codex bridge setups.

## Usage

### Voice Input

Voice input can be started from the glasses HUD with a long press on the temple touchpad. JSOS Core handles recognition and sends `voice_state` / `voice_result` updates back to JSOS HUD.

JSOS separates normal speech-to-text from bidirectional OpenClaw Live Talk:

- **OpenAI Realtime speech-to-text** and **Android SpeechRecognizer** recognize voice input and submit text to OpenClaw.
- **Core Agent Wake** runs from the phone voice UI and keeps phone-side OpenAI Realtime transcription active. A configured leading session label switches to that session and sends the remaining text; follow-up phrases without a new leading label continue in the active session.
- **Core Live Talk** starts OpenClaw Live Talk directly from the phone and routes output to the phone speaker.
- **Glasses Voice Button / CMD** routes the glasses voice button to normal command/input speech recognition.
- **Glasses Voice Button / LIVE TALK** routes the glasses voice button to OpenClaw Live Talk and Rokid communication audio.

Live Talk includes a client-side barge-in path for interrupting assistant output while new user speech is detected. The exact behavior still depends on the target OpenClaw Gateway version and audio route.

Core Agent Wake is designed for fast hands-free routing between configured visible JSOS sessions. For example, saying a configured session label followed by the message selects that session and sends the remaining phrase; later phrases without a new leading label continue in the active session until another configured label is detected. Common transcription variants can be handled in the router, but internal OpenClaw session keys remain unchanged.

Current limitation: Core Agent Wake uses the phone-side voice path. The glasses voice button still uses the normal HUD voice flow, and a glasses-side Agent Wake mode without pressing the voice button is planned as separate follow-up work.

### HUD Controls

| HUD action | Purpose |
| --- | --- |
| Swipe forward | Scroll up or move to the previous focused HUD item. |
| Swipe backward | Scroll down or move to the next focused HUD item. |
| Tap | Confirm the selected HUD action, send staged input, or scroll to the latest content depending on focus. |
| Double tap | Move focus to menu/input areas, cancel overlays, or show the exit confirmation depending on context. |
| Long press | Start voice input. |
| Photo | Request a glasses photo capture through JSOS Core. One staged photo can be previewed, removed, and attached to the next send. |
| Codex | Open the experimental Admin Codex HUD terminal view. |
| Sess | Open the session picker and session state display. |
| Mic | Toggle the HUD voice send mode. |
| Size | Cycle the HUD display mode between Full, Bottom, and Mid. |
| / | Open the COMMANDS panel directly. |
| More | Open the OPTIONS panel for send mode, AR tools, display/font settings, and TTS toggling. |

The HUD has separate focus areas for content, staged input/photos, and the bottom menu. This keeps reading, staging, and command actions usable on the limited glasses touchpad surface.

JSOS HUD can use a directly paired R08 ring as a lightweight glasses remote. `MORE -> RING` exposes pair, forget, accessibility setup, Bluetooth settings, and status refresh actions. The ring is configured over BLE/GATT, then JSOS handles global HID/media-key events through its Accessibility Service. Tap confirms or controls media, double tap goes back, previous/next become swipe navigation, launcher/system panels are driven by Accessibility gestures, and camera capture uses Rokid's scene command path.

The bottom HUD menu uses two compact pages:

- Page 1: `CAM | CODEX | MIC | AR`
- Page 2: `SIZE | MORE | CMD | SESS`

`CODEX` opens the experimental Admin Codex HUD terminal. Inside that view, the bottom menu also uses compact buttons:

- Page 1: `CAM | JSOS | MIC | SEND`
- Page 2: `LINK | STOP | CLEAR`

`JSOS` returns to the normal glasses HUD. `MORE` opens a compact OPTIONS panel:

- `SEND ASK` / `SEND AUTO` toggles staged versus automatic voice-send behavior.
- `AR TOOLS` opens `AR PIC`, `AR REC`, and `AR STOP`.
- `DISPLAY` opens `COMPACT`, `NORMAL`, `COMFORT`, and `LARGE` font presets.
- `TTS ON` / `TTS OFF` toggles response voice state.

### Camera

The HUD can request a camera capture through JSOS Core. Captured photos are staged as a thumbnail on the glasses and attached to the next input sent to OpenClaw. JSOS keeps one staged photo at a time; a new capture replaces the previous staged photo.

JSOS HUD can also trigger Rokid's own AR picture and mixed-recording scene commands from the `AR TOOLS` submenu:

- `AR PIC` asks the Rokid system to capture an AR picture of the glasses view.
- `AR REC` asks the Rokid system to start an AR mixed recording.
- `AR STOP` asks the Rokid system to stop the AR mixed recording.

This path does not merge media inside JSOS. It delegates capture/processing to Rokid's glasses-side system flow; availability depends on the target Rokid firmware and services.

### Experimental Admin Codex Bridge

JSOS includes an experimental private Admin Codex bridge path for local or trusted private-network setups. This path is separate from OpenClaw: it is intended for a user-managed Codex CLI workspace, not for an OpenClaw agent, Discord bot, or OpenClaw session.

- JSOS Core provides a `Codex` tab with camera/image staging, link, send, stop, and clear controls.
- JSOS HUD opens `CODEX` from the bottom menu with a JSOS-style terminal view, staged input/photos, a `JSOS` return action, local link/send/stop/clear controls, and the same HUD message/input rendering used by the normal glasses chat.
- JSOS Core connects to a user-managed WebSocket bridge using port `18890` and path `/codex-cli`. The default host is derived from the configured OpenClaw host only as a convenience for trusted private-network setups.
- Core and HUD input can be sent to that bridge, and returned output is displayed in the local Codex terminal view. The HUD output auto-scrolls to new responses but remains manually scrollable for review.
- Staged photos can be attached to Admin Codex input when the private bridge supports image payloads. JSOS Core sends JPEG/PNG Base64 image payloads and transcodes other decodable staged image formats to JPEG before sending. Image sends are explicit one-shot requests: JSOS sends the currently staged photos with that input and does not automatically reattach older images to later follow-up turns.
- `CLEAR` clears the local terminal view only; it does not reset the remote Codex session or workspace. A private bridge can additionally support `/new` as a text command to start a fresh remote Codex thread while keeping the bridge service running.

The public repository includes only the Android client-side path. It does not include a hosted bridge service, Codex authentication, private server configuration, OpenClaw server configuration, or any credentials. Keep this bridge on a trusted private network and do not expose it directly to the public internet.

### Text-To-Speech

TTS uses ElevenLabs when configured in JSOS Core. Voice responses can be controlled from the phone UI and from the HUD toggle path. JSOS Core sends `tts_state` updates to the HUD so the glasses UI can reflect the current state.

### Wake-On-Message

JSOS Core can wake the glasses display when new streamed content or proactive messages arrive. The phone side wakes the hardware through the Rokid CXR-M path, sends a `wake_signal`, waits for `wake_ack`, and then delivers buffered messages. A keep-alive path helps keep the display awake during longer streamed responses.

### Display

JSOS HUD targets the Rokid glasses portrait display used by this project:

- 480 x 640 portrait HUD canvas.
- Green monochrome HUD rendering.
- Monospace text rendering in the HUD.
- Four font presets: Compact, Normal, Comfortable, and Large.
- Three HUD display modes: Full, Bottom, and Mid.
- Safe-zone placement to avoid problematic display areas and low-brightness fill artifacts.

## Developer Notes

### Emulator Testing

JSOS includes a debug WebSocket bridge for emulator-style testing without a live Bluetooth/CXR glasses connection.

- JSOS Core can enable Debug Mode from Settings. This starts a loopback-only phone-side WebSocket server on port `8081`.
- JSOS HUD detects emulator/debug conditions and connects to `10.0.2.2:8081`.
- The settings UI exposes the ADB forwarding helper:

```bash
adb forward tcp:8081 tcp:8081
```

Typical emulator flow:

1. Run JSOS Core in an Android emulator.
2. Enable Debug Mode in JSOS Core settings.
3. Apply the ADB port forward shown by the app if needed.
4. Run JSOS HUD in an emulator with a 480 x 640 style portrait display.

### OpenClaw Protocol

JSOS Core implements the OpenClaw Gateway WebSocket client. The current client advertises `minProtocol` `4` and `maxProtocol` `5` during connect, then stores the negotiated gateway protocol from the `hello-ok` response for display in JSOS Core. This is separate from the JSOS Core-to-HUD protocol shown in the HUD cards.

Implemented Gateway request/event areas in this source tree include:

- `connect` with token auth, Ed25519 device identity, and optional stored device token.
- `chat.send` for normal message sending, including optional image content.
- `chat.history` for session history loading.
- `sessions.list`, `session.create`, and `sessions.reset` / new-session behavior.
- `talk.session.create`, `talk.session.appendAudio`, `talk.session.cancelOutput`, `talk.session.close`, `talk.session.submitToolResult`, and `talk.event` paths for experimental Live Talk support.
- Gateway `chat` events with delta/final handling for streamed assistant output.
- Auto-reconnect behavior after disconnects.

### Phone-Glasses Protocol

Phone-to-glasses and glasses-to-phone messages are JSON payloads sent over Rokid CXR, or over the debug WebSocket bridge in emulator/debug mode.

Common phone-to-glasses messages:

- `chat_message`
- `agent_thinking`
- `chat_stream`
- `chat_stream_end`
- `chat_history`
- `connection_update`
- `session_list`
- `voice_state`
- `voice_result`
- `photo_result`
- `cli_status`
- `cli_output`
- `cli_error`
- `wake_signal`
- `tts_state`

Common glasses-to-phone messages:

- `user_input`
- `take_photo`
- `remove_photo`
- `list_sessions`
- `switch_session`
- `create_session`
- `slash_command`
- `start_voice`
- `cancel_voice`
- `request_more_history`
- `wake_ack`
- `tts_toggle`
- `cli_connect`
- `cli_disconnect`
- `cli_input`
- `cli_stop`

Direct voice and Realtime are represented by the voice message flow: JSOS HUD sends `start_voice`, JSOS Core replies with `voice_state` including the recognition mode (`openai`, `device`, or `live`), and then sends `voice_result`. OpenAI Realtime speech-to-text runs phone-side. Core Agent Wake also runs phone-side and routes leading agent names through JSOS Core's session resolver before sending text to OpenClaw. OpenClaw Live Talk uses the `talk.session.*` / `talk.event` gateway protocol paths and can be started either from Core Live Talk on the phone or from the glasses voice button in `LIVE TALK` mode.

The experimental Admin Codex terminal uses `cli_connect`, `cli_input`, `cli_stop`, `cli_status`, and `cli_output` messages between JSOS HUD and JSOS Core. JSOS Core then talks to the private Admin Codex bridge over its own WebSocket connection. When staged photos are present, Core can attach them to the bridge request as one-shot image payloads.

Large phone-to-glasses JSON payloads are split into `chunk_part` messages and reassembled on JSOS HUD.

## Troubleshooting

### Connection Refused Or App Will Not Connect

- Verify that the OpenClaw Gateway is running.
- Check the gateway host, port, and token in JSOS Core.
- Make sure the gateway is reachable from the phone. For LAN testing, the gateway usually needs to bind to a LAN-reachable address, not only `127.0.0.1`.
- Keep remote access private. Use a trusted private network instead of exposing the gateway directly to the public internet.
- If you use a full gateway URL, use `ws://` only for local/private cleartext setups such as localhost or a trusted private network; use `wss://` when your gateway supports TLS.

### Pairing Required Or First Connection Fails

This can be normal on first connection. OpenClaw may reject the first connection until the JSOS Core device identity is approved on the gateway.

```bash
openclaw devices list
openclaw devices approve <requestId>
```

After approval, reconnect from JSOS Core. The device token is stored locally by the app for later connections.

### App Crashes On Startup

- Confirm Rokid credentials were saved in JSOS Core if Rokid pairing or HUD deployment fails.
- Confirm the project is opened as the root Gradle project, not as a single subdirectory.
- Use a clean debug build if local build artifacts look stale.
- Do not add release signing files unless you are intentionally preparing a local release build.

### Glasses App Installation Fails

- Rebuild `:glasses-app:assembleDebug` so you have a fresh JSOS HUD debug APK.
- In JSOS Core, select the JSOS HUD APK in the HUD Deployment section, authorize Hi Rokid when prompted, and install through the Hi Rokid / CXR-L flow.
- Hi Rokid must be installed on the phone and already connected to the glasses before JSOS Core can hand off the install.
- Current Global Hi Rokid builds require a normal density-specific PNG launcher icon and the caller package to be included during the CXR-L service bind. This source tree includes that compatibility path for JSOS Core.
- If the JSOS Core deployment flow times out or reports that the Hi Rokid/glasses link dropped, open Hi Rokid, confirm the glasses connection, then retry the same selected APK.
- Manual Hi Rokid / APK Manager installation remains a fallback if the integrated deployment flow is not stable on a device.

### Voice Recognition Not Working

- Without an OpenAI API key, JSOS falls back to Android SpeechRecognizer.
- For OpenAI Realtime voice recognition, configure an OpenAI API key in JSOS Core and enable OpenAI voice recognition.
- Make sure microphone permission is granted on the phone.
- If OpenAI voice fails, JSOS should report fallback behavior and continue with device recognition where available.

### No Audio Or TTS Not Working

- Configure an ElevenLabs API key and voice in JSOS Core.
- Make sure voice responses / TTS are enabled in JSOS Core or from the HUD toggle path.
- Check the phone audio route and volume. The TTS playback path is phone-side.
- For bidirectional Live Talk audio on the phone, use `Voice` -> `Core Live Talk` -> `START PHONE LIVE`. The normal phone mic / Realtime Whisper path is speech-to-text and returns text unless TTS is enabled.
- If TTS is disabled, JSOS still displays text responses on the phone and HUD.

### Glasses HUD Does Not Wake Or Update

- Check that JSOS Core is connected to the glasses and that Wake On Stream is enabled.
- Confirm the glasses are still paired and reachable through Rokid CXR.
- If messages arrive while the display is sleeping, JSOS Core sends `wake_signal` and expects `wake_ack` from JSOS HUD before flushing buffered content.

### Emulator Debug Connection Fails

- Enable Debug Mode in JSOS Core settings.
- Apply the forwarding command shown in the app:

```bash
adb forward tcp:8081 tcp:8081
```

- Confirm JSOS HUD is running in an emulator/debug context so it connects to `10.0.2.2:8081`.

## Security Notes

Do not publish:

- `local.properties`
- `jsos-release.properties`
- `signing.properties`
- `*.jks`
- `.env` or `.env.*`
- private keys, tokens, API keys, access keys, client secrets, or signing passwords
- built APKs from private local configurations
- any manually copied APK assets under `phone-app/src/main/assets/`

Additional notes:

- `ws://` / cleartext traffic is intended for local or private OpenClaw setups. Bare host entries intentionally remain `ws://host:port` for compatibility with local gateways. Prefer trusted private-network access and use an explicit `wss://` URL when your gateway supports TLS.
- Do not expose an OpenClaw Gateway directly to the public internet just to use JSOS.
- Do not expose an experimental Admin Codex bridge directly to the public internet. Keep it on a trusted private network and manage Codex authentication outside the public repository.
- Do not distribute APKs built with real Rokid, OpenAI, ElevenLabs, OpenClaw, or signing credentials.
- Runtime OpenClaw, OpenAI, ElevenLabs, and device-identity secrets are stored in Android Keystore-backed encrypted app storage. Non-secret UI preferences and some Rokid pairing metadata remain local app data.
- JSOS suppresses verbose Rokid CXR SDK runtime logging before Bluetooth connection, but local device logs should still be treated as private diagnostic data.
- JSOS uses generic public-safe UI/log messages for gateway, Codex bridge, voice, Live Talk, Rokid, and Bluetooth failures. Device names, plain serial numbers, raw SDK errors, and raw server exception text should not be published from local logs or screenshots.
- Avoid publishing logs, screenshots, or APKs that contain transcripts, session keys, API keys, device identifiers, or account data.

## Attribution And License

JSOS is based on the upstream [Clawsses](https://github.com/dweddepohl/clawsses) Android glasses project.

Original upstream copyright remains with Pohlster BV and the original contributors. JSOS modifications are by Whatsskill, 2026.

This project is distributed under the GNU Affero General Public License, version 3. See `LICENSE` and `COPYRIGHT`.

Modified versions distributed as APKs or made available over a network must follow the AGPL terms, including preserving notices and making corresponding source available where required.

The Rokid CXR SDK is proprietary software from Rokid and is licensed separately by Rokid. It is not covered by the AGPL license of this repository and must be obtained under Rokid's own terms. JSOS includes a small CXR-L compatibility AAR derived from Rokid's public Maven `client-l:1.0.1` artifact for the Hi Rokid deployment flow; see `phone-app/libs/README.md`.

This fork does not grant a separate commercial or closed-source license. Commercial permissions for upstream rights must be obtained from the original rights holders.

## Thanks

Thanks to:

- [dweddepohl](https://github.com/dweddepohl)
- [OpenClaw](https://github.com/openclaw/openclaw)
- [Anezium](https://github.com/Anezium)
- [Rokid](https://github.com/rokid)
