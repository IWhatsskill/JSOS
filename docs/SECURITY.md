# Security Notes

This document collects public-safe security guidance for JSOS.

## Do Not Publish

Do not publish:

- `local.properties`
- `jsos-release.properties`
- `signing.properties`
- `*.jks`
- `.env` or `.env.*`
- private keys, tokens, API keys, access keys, client secrets, or signing passwords
- built APKs from private local configurations
- any manually copied APK assets under `phone-app/src/main/assets/`
- logs, screenshots, or APKs containing transcripts, session keys, API keys, device identifiers, or account data

## Network Exposure

`ws://` / cleartext traffic is intended for local or private OpenClaw setups. Bare host entries intentionally remain `ws://host:port` for compatibility with local gateways.

Use trusted private-network access and use an explicit `wss://` URL when your gateway supports TLS.

Do not expose an OpenClaw Gateway directly to the public internet just to use JSOS.

Do not expose an experimental Admin Codex bridge directly to the public internet. Keep it on a trusted private network and manage Codex authentication outside the public repository.

## Runtime Secrets

Runtime OpenClaw, OpenAI, ElevenLabs, and device-identity secrets are stored in Android Keystore-backed encrypted app storage. Non-secret UI preferences and some Rokid pairing metadata remain local app data.

JSOS Core does not compile Rokid CXR credentials into the APK. The phone app asks for the Rokid access key and client secret at runtime in the HUD/Rokid settings area, stores them locally, and uses them for Rokid CXR pairing, SN verification, and HUD deployment.

## Logs And Screenshots

JSOS suppresses verbose Rokid CXR SDK runtime logging before Bluetooth connection, but local device logs should still be treated as private diagnostic data.

JSOS uses generic public-safe UI/log messages for gateway, Codex bridge, voice, Live Talk, Rokid, and Bluetooth failures. Device names, plain serial numbers, raw SDK errors, and raw server exception text should not be published from local logs or screenshots.

Avoid publishing logs, screenshots, or APKs that contain:

- transcripts
- session keys
- API keys
- OpenClaw tokens
- Rokid credentials
- MAC addresses
- serial numbers
- device names
- account data

## Release Artifacts

Generated APKs are local build artifacts. Public repositories should contain source code and public documentation, not APKs built with local credentials or signing configuration.

The repository ignores generated APKs and local signing material. Keep release preparation in a private signing environment.
