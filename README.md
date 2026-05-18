# JSOS

**Spatial Operating System for Smart Glasses**

**JSOS** is an Android-based smart-glasses interface for a local or private **OpenClaw Gateway** and **Rokid glasses**.

It consists of two Android apps:

- **JSOS Core** - the phone app. It connects to OpenClaw, stores local runtime settings, handles voice input, TTS settings, sessions, camera handoff, Rokid pairing, and HUD deployment.
- **JSOS HUD** - the glasses app. It renders the lightweight HUD, receives streamed chat updates, handles touchpad gestures, stages voice input, displays sessions, requests photo capture, and can trigger Rokid AR picture/recording scenes.

JSOS started as a fork of the upstream Clawsses project and has been substantially reworked into a separate development-preview project. It is being prepared as a public fork base and is not presented as a finished consumer product.

<p align="center">
  <img src="docs/images/Top-Picture.png" alt="JSOS smart-glasses HUD preview" width="100%">
</p>

## Showcase

Download a 90-second JSOS HUD showcase video:

[Download the JSOS showcase video](docs/videos/JSOS-showcase.mp4)

## Status

Current state:

- Android multi-module project with `phone-app`, `glasses-app`, and `shared` modules.
- JSOS Core and JSOS HUD app labels, package namespaces, and launcher branding use JSOS naming.
- Debug builds are the supported local development path.
- OpenClaw Gateway integration, Rokid CXR transport, sessions, streaming chat, voice input, Core/Glasses Live Talk routing, optional ElevenLabs TTS, wake signaling, Hi Rokid / CXR-L HUD deployment, and Rokid AR picture/recording triggers are present in this source tree.
- Selected screenshots and visual assets are referenced for public documentation. They should remain neutral and redacted before publication.

Development-preview areas:

- Rokid device behavior depends on the proprietary Rokid CXR SDK and device firmware.
- OpenClaw Live Talk support is present in the JSOS codebase, but should be treated as experimental unless tested against the target OpenClaw Gateway version and audio route.
- Release signing is intentionally local-only and requires private signing properties that must not be published.
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
- Restyled existing OPTIONS, COMMANDS, and SESSIONS HUD panels into the JSOS green monochrome HUD design, with low-brightness-friendly outlines, no filled selection backgrounds, direct bottom-bar slash command access, and grouped `AR TOOLS` / `DISPLAY` submenus.
- Staged voice input on the glasses with `Send Ask` and `Send Auto` modes.
- Session picker presentation updates, current-session markers, unread indicators, and session/chat forwarding behavior.
- OpenClaw Gateway protocol negotiation used by JSOS, currently advertising a v4-v5 client range and showing the negotiated gateway protocol in JSOS Core.
- OpenAI Realtime voice input path with Android SpeechRecognizer fallback.
- Core Agent Wake mode for phone-side continuous OpenAI Realtime transcription, leading-agent-name session routing, follow-up messages in the active session, and alias handling for the visible JSOS sessions (`JARVIS`, `WhatsApp`, `Gideon`, `Chappi`, `Goku`, `Steel`, `Shelli`, `General`).
- Updated the existing ElevenLabs TTS, Rokid CXR transport, and HUD camera request flows for the JSOS codebase, current dependencies, JSOS UI, and public-safe logging.
- Integrated a Hi Rokid / CXR-L HUD deployment flow in JSOS Core so the phone app can select a JSOS HUD APK and hand installation to Hi Rokid when Hi Rokid is installed and already connected to the glasses.
- Added a JSOS-built `client-l:1.0.1` compatibility artifact derived from Rokid's public Maven artifact, stripped only of duplicate classes/native libraries already supplied by `client-m:1.2.1`.
- Hardened the Hi Rokid / CXR-L deployment flow with link reset, Bluetooth/CXR readiness timeouts, stable-link delay before upload, and retry-friendly failure messages.
- Added JSOS HUD `AR PIC` and `AR REC` options that trigger Rokid AR picture / mixed-recording scene commands from the glasses app.
- Chunked phone-to-glasses message transport for larger JSON payloads.
- Wake acknowledgments and status messages between phone and HUD.
- Public-readiness cleanup: neutral assets, README rewrite, safer `.gitignore`, redacted sensitive logs, local-only signing, and clearer security notes.

## Quick Start

1. Install Android Studio or use a working Android Gradle environment with JDK 17.
2. Add local Rokid CXR credentials to `local.properties`.
3. Build both debug APKs from the project root:

```bash
./gradlew :phone-app:assembleDebug :glasses-app:assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :phone-app:assembleDebug :glasses-app:assembleDebug
```

4. Install JSOS Core on the phone:

```bash
adb install phone-app/build/outputs/apk/debug/phone-app-debug.apk
```

5. Use JSOS Core to configure OpenClaw, approve the phone device on the gateway if required, and pair the Rokid glasses.
6. Install the separate JSOS HUD APK on the glasses either from JSOS Core's HUD Deployment section, which uses Hi Rokid / CXR-L, or manually through Hi Rokid / APK Manager.

## Architecture

| Component | Link | Responsibilities |
| --- | --- | --- |
| OpenClaw Gateway | WebSocket to JSOS Core | AI sessions, chat streaming, tool execution |
| JSOS Core / Phone App | WebSocket to OpenClaw; Bluetooth CXR to JSOS HUD | Bridge + voice, TTS playback, wake management |
| JSOS HUD / Glasses App | Bluetooth CXR messages from JSOS Core; local Rokid scene commands | HUD + gestures, camera capture, AR picture/record triggers, session picker |

## Repository Layout

| Path | Purpose |
| --- | --- |
| `phone-app/` | JSOS Core Android phone app. Bridges OpenClaw, Rokid CXR, voice, TTS, sessions, camera handoff, and HUD deployment. |
| `glasses-app/` | JSOS HUD Android glasses app. Renders the HUD, handles gestures, sessions, staged input, voice state, camera requests, and Rokid AR picture/record triggers. |
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
- Optional: Tailscale or another VPN for private remote access to the gateway.

## Local Configuration

Create `local.properties` in the project root for local Rokid CXR credentials:

```properties
rokid.clientSecret=your-client-secret
rokid.accessKey=your-access-key
```

These values are injected into the phone app at build time as `BuildConfig` fields and are needed for Rokid CXR pairing and verification. Runtime OpenClaw, OpenAI, and ElevenLabs values are configured inside JSOS Core.

Do not commit or publish `local.properties`.

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

For remote access, use a private VPN such as Tailscale instead of exposing the gateway directly to the public internet. `ws://` is cleartext at the app layer and should only be used over localhost, trusted LAN, or VPN/Tailnet links.

On first connection, OpenClaw may require device approval:

```bash
openclaw devices list
openclaw devices approve <requestId>
```

The first connection attempt may fail with a pairing or approval error until the gateway device request is approved. After approval, reconnect from JSOS Core.

## Build And Install

The public repository contains source code and documentation only. It does not
ship official APK releases, private signing keys, or device/API credentials.

JSOS Core and JSOS HUD are built and installed as separate Android apps:

- JSOS Core is installed on the phone.
- JSOS HUD is installed on the glasses through Hi Rokid. JSOS Core can drive this from its HUD Deployment section when Hi Rokid is installed and the glasses are already connected there; manual Hi Rokid / APK Manager installation remains a fallback.

### Debug Builds

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

Do not publish private debug APKs built with real local credentials.

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
- Debug WebSocket bridge for emulator-style local testing.
- OpenAI Realtime speech-to-text when configured.
- Core Agent Wake: phone-side continuous Realtime transcription with leading-agent-name routing into the visible JSOS sessions.
- Android SpeechRecognizer fallback.
- Optional ElevenLabs TTS settings and playback path.
- Photo capture handoff from the glasses to the phone/OpenClaw flow.
- HUD deployment flow for selecting a separate JSOS HUD APK and handing installation to Hi Rokid / CXR-L.

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
- Camera request flow and photo thumbnail staging.
- Rokid AR Picture and AR Record scene triggers from the HUD AR TOOLS submenu.
- Wake acknowledgments and TTS toggle messages back to the phone.

## Usage

### Voice Input

Voice input can be started from the glasses HUD with a long press on the temple touchpad. JSOS Core handles recognition and sends `voice_state` / `voice_result` updates back to JSOS HUD.

JSOS separates normal speech-to-text from bidirectional OpenClaw Live Talk:

- **OpenAI Realtime speech-to-text** and **Android SpeechRecognizer** recognize voice input and submit text to OpenClaw.
- **Core Agent Wake** runs from the phone voice UI and keeps phone-side OpenAI Realtime transcription active. A leading agent name such as `Shelli ...` switches to that session and sends the remaining text; follow-up phrases without a new agent name continue in the active session.
- **Core Live Talk** starts OpenClaw Live Talk directly from the phone and routes output to the phone speaker.
- **Glasses Voice Button / CMD** routes the glasses voice button to normal command/input speech recognition.
- **Glasses Voice Button / LIVE TALK** routes the glasses voice button to OpenClaw Live Talk and Rokid communication audio.

Core Agent Wake is designed for fast hands-free routing between the visible JSOS sessions. For example, `Shelli what is on the plan today` selects Shelli and sends `what is on the plan today`; a later phrase without an agent name continues in Shelli until another leading name is detected. Common transcription variants for the current agent names are handled in the router, but internal OpenClaw session keys remain unchanged.

Current limitation: Core Agent Wake uses the phone-side voice path. The glasses voice button still uses the normal HUD voice flow, and a glasses-side Agent Wake mode without pressing the voice button is planned as separate follow-up work.

### HUD Controls

| HUD action | Purpose |
| --- | --- |
| Swipe forward | Scroll up or move to the previous focused HUD item. |
| Swipe backward | Scroll down or move to the next focused HUD item. |
| Tap | Confirm the selected HUD action, send staged input, or scroll to the latest content depending on focus. |
| Double tap | Move focus to menu/input areas, cancel overlays, or show the exit confirmation depending on context. |
| Long press | Start voice input. |
| Photo | Request a glasses photo capture through JSOS Core. Up to four staged photos can be attached. |
| Sess | Open the session picker and session state display. |
| Size | Cycle the HUD display mode between Full, Bottom, and Mid. |
| / | Open the COMMANDS panel directly. |
| More | Open the OPTIONS panel for send mode, AR tools, display/font settings, and TTS toggling. |

The HUD has separate focus areas for content, staged input/photos, and the bottom menu. This keeps reading, staging, and command actions usable on the limited glasses touchpad surface.

The bottom HUD menu is `CAM | SESS | SIZE | / | MORE`. `MORE` opens a compact OPTIONS panel:

- `SEND ASK` / `SEND AUTO` toggles staged versus automatic voice-send behavior.
- `AR TOOLS` opens `AR PIC`, `AR REC`, and `AR STOP`.
- `DISPLAY` opens `COMPACT`, `NORMAL`, `COMFORT`, and `LARGE` font presets.
- `TTS ON` / `TTS OFF` toggles response voice state.

### Camera

The HUD can request a camera capture through JSOS Core. Captured photos are staged as thumbnails on the glasses and attached to the next input sent to OpenClaw. JSOS limits staged photos to four.

JSOS HUD can also trigger Rokid's own AR picture and mixed-recording scene commands from the `AR TOOLS` submenu:

- `AR PIC` asks the Rokid system to capture an AR picture of the glasses view.
- `AR REC` asks the Rokid system to start an AR mixed recording.
- `AR STOP` asks the Rokid system to stop the AR mixed recording.

This path does not merge media inside JSOS. It delegates capture/processing to Rokid's glasses-side system flow; availability depends on the target Rokid firmware and services.

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
- `talk.session.create`, `talk.session.appendAudio`, `talk.session.close`, `talk.session.submitToolResult`, and `talk.event` paths for experimental Live Talk support.
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

Direct voice and Realtime are represented by the voice message flow: JSOS HUD sends `start_voice`, JSOS Core replies with `voice_state` including the recognition mode (`openai`, `device`, or `live`), and then sends `voice_result`. OpenAI Realtime speech-to-text runs phone-side. Core Agent Wake also runs phone-side and routes leading agent names through JSOS Core's session resolver before sending text to OpenClaw. OpenClaw Live Talk uses the `talk.session.*` / `talk.event` gateway protocol paths and can be started either from Core Live Talk on the phone or from the glasses voice button in `LIVE TALK` mode.

Large phone-to-glasses JSON payloads are split into `chunk_part` messages and reassembled on JSOS HUD.

## Screenshots And Images

The README references selected JSOS assets and screenshots.

### JSOS Core

#### Home, Chat, Voice, Diagnostics

<p align="center">
  <img src="docs/images/jsos-core-home.jpeg" alt="JSOS Core home dashboard" width="22%">
  <img src="docs/images/jsos-core-session-chat.jpeg" alt="JSOS Core session chat view" width="22%">
  <img src="docs/images/jsos-core-voice.jpeg" alt="JSOS Core voice settings and live talk mode" width="22%">
  <img src="docs/images/jsos-core-diagnostics.jpeg" alt="JSOS Core diagnostics dashboard" width="22%">
</p>

#### Config

<p align="center">
  <img src="docs/images/jsos-core-system-link.jpeg" alt="JSOS Core OpenClaw system link settings" height="170">
  <img src="docs/images/jsos-core-hud-link.png" alt="JSOS Core HUD link settings with redacted device name" height="170">
  <img src="docs/images/jsos-core-hud-deployment.jpeg" alt="JSOS Core HUD deployment settings" height="170">
  <img src="docs/images/jsos-core-voice-matrix.jpeg" alt="JSOS Core OpenAI voice matrix settings with redacted API key" height="170">
  <img src="docs/images/jsos-core-response-voice.jpeg" alt="JSOS Core response voice settings with redacted API key" height="170">
</p>

### JSOS HUD

<p align="center">
  <img src="docs/images/jsos-hud-glasses-01.jpeg" alt="JSOS HUD options overlay captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-glasses-02.jpeg" alt="JSOS HUD slash commands option captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-glasses-03.jpeg" alt="JSOS HUD font option captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-glasses-04.jpeg" alt="JSOS HUD exit overlay captured through Rokid glasses" width="19%">
  <img src="docs/images/jsos-hud-glasses-05.jpeg" alt="JSOS HUD sessions overlay captured through Rokid glasses" width="19%">
</p>

## Troubleshooting

### Connection Refused Or App Will Not Connect

- Verify that the OpenClaw Gateway is running.
- Check the gateway host, port, and token in JSOS Core.
- Make sure the gateway is reachable from the phone. For LAN testing, the gateway usually needs to bind to a LAN-reachable address, not only `127.0.0.1`.
- Keep remote access private. Use a VPN such as Tailscale instead of exposing the gateway directly to the public internet.
- If you use a full gateway URL, use `ws://` only for local/private cleartext setups such as localhost, trusted LAN, or VPN/Tailnet; use `wss://` when your gateway supports TLS.

### Pairing Required Or First Connection Fails

This can be normal on first connection. OpenClaw may reject the first connection until the JSOS Core device identity is approved on the gateway.

```bash
openclaw devices list
openclaw devices approve <requestId>
```

After approval, reconnect from JSOS Core. The device token is stored locally by the app for later connections.

### App Crashes On Startup

- Confirm that `local.properties` exists for local builds that need Rokid CXR credentials.
- Confirm the project is opened as the root Gradle project, not as a single subdirectory.
- Use a clean debug build if local build artifacts look stale.
- Do not add release signing files unless you are intentionally preparing a local release build.

### Glasses App Installation Fails

- Rebuild `:glasses-app:assembleDebug` so you have a fresh JSOS HUD debug APK.
- In JSOS Core, select the JSOS HUD APK in the HUD Deployment section, authorize Hi Rokid when prompted, and install through the Hi Rokid / CXR-L flow.
- Hi Rokid must be installed on the phone and already connected to the glasses before JSOS Core can hand off the install.
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

- `ws://` / cleartext traffic is intended for local or private OpenClaw setups. Bare host entries intentionally remain `ws://host:port` for compatibility with local gateways. Prefer trusted LAN/VPN/Tailnet access and use an explicit `wss://` URL when your gateway supports TLS.
- Do not expose an OpenClaw Gateway directly to the public internet just to use JSOS.
- Do not distribute APKs built with real Rokid, OpenAI, ElevenLabs, OpenClaw, or signing credentials.
- Runtime OpenClaw, OpenAI, ElevenLabs, and device-identity secrets are stored in Android Keystore-backed encrypted app storage. Non-secret UI preferences and some Rokid pairing metadata remain local app data.
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
