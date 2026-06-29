# Troubleshooting

## Connection Refused Or App Will Not Connect

- Verify that the OpenClaw Gateway is running.
- Check the gateway host, port, and token in JSOS Core.
- Make sure the gateway is reachable from the phone. For LAN testing, the gateway usually needs to bind to a LAN-reachable address, not only `127.0.0.1`.
- Keep remote access private. Use a trusted private network instead of exposing the gateway directly to the public internet.
- If you use a full gateway URL, use `ws://` only for local/private cleartext setups such as localhost or a trusted private network; use `wss://` when your gateway supports TLS.

## Pairing Required Or First Connection Fails

This can be normal on first connection. OpenClaw may reject the first connection until the JSOS Core device identity is approved on the gateway.

```bash
openclaw devices list
openclaw devices approve <requestId>
```

After approval, reconnect from JSOS Core. The device token is stored locally by the app for later connections.

## App Crashes On Startup

- Confirm Rokid credentials were saved in JSOS Core if Rokid pairing or HUD deployment fails.
- Confirm the project is opened as the root Gradle project, not as a single subdirectory.
- Use a clean debug build if local build artifacts look stale.
- Do not add release signing files unless you are intentionally preparing a local release build.

## Glasses App Installation Fails

- Rebuild `:glasses-app:assembleDebug` so you have a fresh JSOS HUD debug APK.
- In JSOS Core, select the JSOS HUD APK in the HUD Deployment section, authorize Hi Rokid when prompted, and install through the Hi Rokid / CXR-L flow.
- Hi Rokid must be installed on the phone and already connected to the glasses before JSOS Core can hand off the install.
- Current Global Hi Rokid builds require a normal density-specific PNG launcher icon and the caller package to be included during the CXR-L service bind. This source tree includes that compatibility path for JSOS Core.
- If the JSOS Core deployment flow times out or reports that the Hi Rokid/glasses link dropped, open Hi Rokid, confirm the glasses connection, then retry the same selected APK.
- Manual Hi Rokid / APK Manager installation remains a fallback if the integrated deployment flow is not stable on a device.

## Voice Recognition Not Working

- Without an OpenAI API key, JSOS falls back to Android SpeechRecognizer.
- For OpenAI Realtime voice recognition, configure an OpenAI API key in JSOS Core and enable OpenAI voice recognition.
- Make sure microphone permission is granted on the phone.
- If OpenAI voice fails, JSOS should report fallback behavior and continue with device recognition where available.

## No Audio Or TTS Not Working

- Configure an ElevenLabs API key and voice in JSOS Core.
- Make sure voice responses / TTS are enabled in JSOS Core or from the HUD toggle path.
- Check the selected voice output route and volume. TTS can play on the phone, glasses, or watch when configured.
- For bidirectional Live Talk audio on the phone, use `Voice` -> `Core Live Talk` -> `START PHONE LIVE`. Watch realtime audio output is experimental and depends on Wear OS Data Layer latency.
- The normal phone mic / Realtime Whisper path is speech-to-text and returns text unless TTS is enabled.
- If TTS is disabled, JSOS still displays text responses on the phone and HUD.

## Glasses HUD Does Not Wake Or Update

- Check that JSOS Core is connected to the glasses and that Wake On Stream is enabled.
- Confirm the glasses are still paired and reachable through Rokid CXR.
- If messages arrive while the display is sleeping, JSOS Core sends `wake_signal` and expects `wake_ack` from JSOS HUD before flushing buffered content.

## Emulator Debug Connection Fails

- Enable Debug Mode in JSOS Core settings.
- Apply the forwarding command shown in the app:

```bash
adb forward tcp:8081 tcp:8081
```

- Confirm JSOS HUD is running in an emulator/debug context so it connects to `10.0.2.2:8081`.
