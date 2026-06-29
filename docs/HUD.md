# HUD, Voice, Camera, And R08 Ring

This document covers the glasses-side JSOS HUD behavior and user-facing control model.

## Display

JSOS HUD targets the Rokid glasses portrait display used by this project:

- 480 x 640 portrait HUD canvas.
- Green monochrome HUD rendering.
- Monospace text rendering in the HUD.
- Four font presets: Compact, Normal, Comfortable, and Large.
- Three HUD display modes: Full, Bottom, and Mid.
- Safe-zone placement to avoid problematic display areas and low-brightness fill artifacts.

The HUD is designed to stay readable over the real world, with minimal fills, high contrast, and touchpad-first navigation.

## HUD Controls

| HUD action | Purpose |
| --- | --- |
| Swipe forward | Scroll up or move to the previous focused HUD item. |
| Swipe backward | Scroll down or move to the next focused HUD item. |
| Tap | Confirm the selected HUD action, send staged input, or scroll to the latest content depending on focus. |
| Double tap | Move focus to menu/input areas, cancel overlays, or show the exit confirmation depending on context. |
| Long press | Start voice input. |
| Photo | Request a glasses photo capture through JSOS Core. |
| Codex | Open the optional Admin Codex HUD terminal view. |
| Sess | Open the session picker and session state display. |
| Mic | Toggle the HUD voice send mode. |
| Size | Cycle the HUD display mode between Full, Bottom, and Mid. |
| `/` | Open the COMMANDS panel directly. |
| More | Open the OPTIONS panel for send mode, AR tools, display/font settings, TTS toggling, and ring setup. |

The HUD has separate focus areas for content, staged input/photos, and the bottom menu. This keeps reading, staging, and command actions usable on the limited glasses touchpad surface.

The bottom HUD menu uses two compact pages:

- Page 1: `CAM | CODEX | MIC | AR`
- Page 2: `SIZE | MORE | CMD | SESS`

`MORE` opens a compact OPTIONS panel:

- `SEND ASK` / `SEND AUTO` toggles staged versus automatic voice-send behavior.
- `AR TOOLS` opens `AR PIC`, `AR REC`, and `AR STOP`.
- `DISPLAY` opens `COMPACT`, `NORMAL`, `COMFORT`, and `LARGE` font presets.
- `TTS ON` / `TTS OFF` toggles response voice state.

## Voice Input

Voice input can be started from the glasses HUD with a long press on the temple touchpad. JSOS Core handles recognition and sends `voice_state` / `voice_result` updates back to JSOS HUD.

JSOS separates normal speech-to-text from bidirectional OpenClaw Live Talk:

- **OpenAI Realtime speech-to-text** and **Android SpeechRecognizer** recognize voice input and submit text to OpenClaw.
- **Core Agent Wake** runs from the phone voice UI and keeps phone-side OpenAI Realtime transcription active. A configured leading session label switches to that session and sends the remaining text; follow-up phrases without a new leading label continue in the active session.
- **Core Live Talk** starts OpenClaw Live Talk directly from the phone and routes output through the selected voice output target.
- **Glasses Voice Button / CMD** routes the glasses voice button to normal command/input speech recognition.
- **Glasses Voice Button / LIVE TALK** routes the glasses voice button to OpenClaw Live Talk and Rokid communication audio.

Live Talk includes a client-side barge-in path for interrupting assistant output while new user speech is detected. The exact behavior still depends on the target OpenClaw Gateway version and audio route. Watch realtime playback is experimental and streams PCM audio through the Wear OS Data Layer.

Current limitation: Core Agent Wake uses the phone-side voice path. The glasses voice button still uses the normal HUD voice flow, and a glasses-side Agent Wake mode without pressing the voice button is planned as separate follow-up work.

## Camera And AR Tools

The HUD can request a camera capture through JSOS Core. Captured photos are staged as a thumbnail on the glasses and attached to the next input sent to OpenClaw. JSOS keeps one staged photo at a time; a new capture replaces the previous staged photo.

JSOS HUD can also trigger Rokid's own AR picture and mixed-recording scene commands from the `AR TOOLS` submenu:

- `AR PIC` asks the Rokid system to capture an AR picture of the glasses view.
- `AR REC` asks the Rokid system to start an AR mixed recording.
- `AR STOP` asks the Rokid system to stop the AR mixed recording.

This path does not merge media inside JSOS. It delegates capture/processing to Rokid's glasses-side system flow; availability depends on the target Rokid firmware and services.

## Direct R08 Ring Control

JSOS HUD can use a directly paired R08 ring as a lightweight glasses remote. `MORE -> RING` exposes pair, configurable `TAP 3` / `TAP 4` actions, forget, accessibility setup, Bluetooth settings, and status refresh actions.

The ring is configured over BLE/GATT, then JSOS handles global HID/media-key events through its Accessibility Service.

Current mappings:

- Tap confirms in JSOS HUD or controls media in Rokid music contexts.
- Double tap starts JSOS voice in the HUD and acts as back in global/system contexts.
- Triple tap defaults to Rokid AI through the `ai_assist` scene command, but can be cycled in `MORE -> RING`.
- Quadruple tap defaults to Rokid photo through the `take_picture` scene command, but can be cycled in `MORE -> RING`.
- `TAP 3` and `TAP 4` can cycle through `AI`, `PHOTO`, `AR PIC`, `AR REC`, `EXIT`, and `NONE`; `AR REC` toggles mixed recording on/off.
- `EXIT` is a HUD-only action: inside JSOS HUD it opens the existing exit confirmation, while the global Accessibility Service ignores it.
- Previous/next become swipe navigation.
- Launcher/system panels are driven by Accessibility gestures where the target Rokid app accepts them.

The current R08 path runs directly on the glasses. Legacy phone-side Bluetooth media-ring forwarding has been removed from JSOS Core.

## Wake-On-Message

JSOS Core can wake the glasses display when new streamed content or proactive messages arrive. The phone side wakes the hardware through the Rokid CXR-M path, sends a `wake_signal`, waits for `wake_ack`, and then delivers buffered messages. A keep-alive path helps keep the display awake during longer streamed responses.

## Text-To-Speech

TTS uses ElevenLabs when configured in JSOS Core. Voice responses can be controlled from the phone UI and from the HUD toggle path. JSOS Core sends `tts_state` updates to the HUD so the glasses UI can reflect the current state.
