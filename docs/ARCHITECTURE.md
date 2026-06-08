# Architecture

JSOS is split into a phone app, a glasses app, a shared protocol module, and optional private bridge clients.

## Components

| Component | Runs on | Responsibilities |
| --- | --- | --- |
| OpenClaw Gateway | User-managed host | AI sessions, chat streaming, tools, device pairing, Live Talk protocol paths. |
| JSOS Core | Android phone | OpenClaw WebSocket client, session management, voice input, TTS routing, Rokid CXR phone connection, HUD deployment, camera handoff, wake management, runtime settings. |
| JSOS HUD | Rokid glasses | Readable glasses UI, touchpad navigation, direct R08 ring input, session output, staged input/photos, photo requests, Rokid scene commands. |
| `shared` module | Both Android apps | Shared JSON protocol models and transport helpers. |
| Admin Codex bridge | User-managed private host | Optional Codex-style output in JSOS Core and JSOS HUD. The bridge service and credentials are not included in this repository. |

## Repository Layout

| Path | Purpose |
| --- | --- |
| `phone-app/` | JSOS Core Android phone app. |
| `glasses-app/` | JSOS HUD Android glasses app. |
| `shared/` | Shared JSON protocol models used between phone, glasses, and OpenClaw-facing code. |
| `docs/` | Public notes, setup details, screenshots, SDK references, and focused development documents. |
| `gradle/` | Gradle wrapper files. |
| `LICENSE` | GNU AGPL license text. |
| `COPYRIGHT` | Fork, attribution, modification, and third-party notices. |

## JSOS Core Phone App

JSOS Core is responsible for:

- OpenClaw Gateway WebSocket connection.
- Token authentication and Ed25519 device identity.
- Session listing, switching, reset/new-session flow, and chat history loading.
- Streaming chat forwarding to the HUD.
- Rokid CXR phone-side connection and device control.
- HUD brightness preference for connected Rokid glasses.
- Debug WebSocket bridge for emulator-style local testing.
- OpenAI Realtime speech-to-text and Android SpeechRecognizer fallback.
- Core Agent Wake mode for phone-side session routing.
- OpenClaw Live Talk start/stop, audio routing, and barge-in signaling.
- Optional ElevenLabs TTS playback on the phone.
- Camera capture request handling and one-photo staging.
- Hi Rokid / CXR-L HUD deployment flow.
- Optional Admin Codex bridge client.

## JSOS HUD Glasses App

JSOS HUD is responsible for:

- Green monochrome HUD rendering for the Rokid display target.
- Touchpad gesture handling.
- Chat stream display and history rendering.
- Session picker UI.
- OPTIONS, COMMANDS, SESSIONS, AR TOOLS, DISPLAY, and RING panels.
- Top-bar voice state display, including wave/live status for active voice modes.
- Reading-first behavior where the bottom menu stays hidden while content is being read and appears when needed.
- Staged voice input with `Send Ask` and `Send Auto` modes.
- Camera request flow with a single staged photo preview and remove action.
- Rokid AR Picture and AR Record scene triggers from the HUD AR TOOLS submenu.
- Direct R08 ring handling on the glasses.
- Wake acknowledgments and TTS toggle messages back to the phone.
- Optional Admin Codex terminal view for private bridge setups.

## OpenClaw Gateway Protocol

JSOS Core implements the OpenClaw Gateway WebSocket client. The current client advertises `minProtocol` `4` and `maxProtocol` `5` during connect, then stores the negotiated gateway protocol from the `hello-ok` response for display in JSOS Core. This is separate from the JSOS Core-to-HUD protocol shown in the HUD cards.

Implemented Gateway request/event areas include:

- `connect` with token auth, Ed25519 device identity, and optional stored device token.
- `chat.send` for normal message sending, including optional image content.
- `chat.history` for session history loading.
- `sessions.list`, `session.create`, and `sessions.reset` / new-session behavior.
- `talk.session.create`, `talk.session.appendAudio`, `talk.session.cancelOutput`, `talk.session.close`, `talk.session.submitToolResult`, and `talk.event` paths for experimental Live Talk support.
- Gateway `chat` events with delta/final handling for streamed assistant output.
- Auto-reconnect behavior after disconnects.

## Phone-Glasses Protocol

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

Direct voice and Realtime are represented by the voice message flow: JSOS HUD sends `start_voice`, JSOS Core replies with `voice_state` including the recognition mode (`openai`, `device`, or `live`), and then sends `voice_result`.

Large phone-to-glasses JSON payloads are split into `chunk_part` messages and reassembled on JSOS HUD.

## Emulator Testing

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
4. Run JSOS HUD in an emulator with a 480 x 640 portrait display.
