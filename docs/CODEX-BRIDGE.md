# Optional Admin Codex Bridge

JSOS includes an experimental Admin Codex bridge client for local or trusted private-network setups.

This path is separate from OpenClaw. It is intended for a user-managed Codex CLI-style workspace, not for an OpenClaw agent, Discord bot, or OpenClaw session.

## What Is Included

- JSOS Core provides a `Codex` tab with camera/image staging, link, send, stop, and clear controls.
- JSOS HUD opens `CODEX` from the bottom menu with a JSOS-style terminal view.
- JSOS HUD includes staged input/photos, a `JSOS` return action, local link/send/stop/clear controls, and the same HUD message/input rendering used by the normal glasses chat.
- JSOS Core connects to a user-managed WebSocket bridge at path `/codex-cli`.
- Full `ws://`, `wss://`, `http://`, or `https://` bridge host URLs are respected, including their host, path, and query. `http://` and `https://` inputs are converted to the matching WebSocket scheme.
- Bare host entries remain a local-development fallback and resolve to `ws://host:18890/codex-cli`.
- The default bridge host is derived from the configured OpenClaw host only as a convenience for trusted private-network setups.
- Core and HUD input can be sent to that bridge, and returned output is displayed in the local Codex terminal view.
- HUD output auto-scrolls to new responses but remains manually scrollable for review.

## Image Payloads

Staged photos can be attached to Admin Codex input when the private bridge supports image payloads.

JSOS Core sends JPEG/PNG Base64 image payloads and transcodes other decodable staged image formats to JPEG before sending. Image sends are explicit one-shot requests: JSOS sends the currently staged photos with that input and does not automatically reattach older images to later follow-up turns.

## Local Clear Versus Remote Session Reset

`CLEAR` clears the local terminal view only. It does not reset the remote Codex session or workspace.

A private bridge can additionally support `/new` as a text command to start a fresh remote Codex thread while keeping the bridge service running.

## What Is Not Included

The public repository includes only the Android client-side path.

It does not include:

- hosted bridge service
- Codex authentication
- private server configuration
- OpenClaw server configuration
- private credentials
- private bridge deployment scripts

Keep this bridge on a trusted private network and do not expose it directly to the public internet.
