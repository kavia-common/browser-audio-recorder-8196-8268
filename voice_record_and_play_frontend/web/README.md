# Ocean Recorder (Web Preview)

This folder contains a **browser-based** audio recorder using the **MediaRecorder** API:

- Start / Stop recording
- Instant playback of the last recording
- Save recordings into a **session list** (in-memory)
- Download / delete individual recordings
- Clear all

## Run locally

Serve this folder with any static file server, then open:

- `web/index.html`

Example (one option):

- `python -m http.server 3000` (run inside the `web/` folder)

> Note: Microphone access requires `http://localhost` or `https://` (secure context).
