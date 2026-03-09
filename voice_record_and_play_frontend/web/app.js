/**
 * Ocean Recorder - browser-only audio recorder using MediaRecorder.
 * No backend; session recordings are kept in memory for the page session.
 */

const els = {
  btnStart: document.getElementById("btnStart"),
  btnStop: document.getElementById("btnStop"),
  btnSave: document.getElementById("btnSave"),
  btnClearAll: document.getElementById("btnClearAll"),
  btnToggleMonitor: document.getElementById("btnToggleMonitor"),

  audioPreview: document.getElementById("audioPreview"),
  previewMeta: document.getElementById("previewMeta"),

  recordingsEmpty: document.getElementById("recordingsEmpty"),
  recordingsList: document.getElementById("recordingsList"),
  sessionSummary: document.getElementById("sessionSummary"),

  statusPill: document.getElementById("statusPill"),
  statusText: document.getElementById("statusText"),
  timerText: document.getElementById("timerText"),
};

const template = /** @type {HTMLTemplateElement} */ (
  document.getElementById("recordingItemTemplate")
);

const state = {
  supported: typeof window !== "undefined" && "MediaRecorder" in window,
  stream: /** @type {MediaStream|null} */ (null),
  mediaRecorder: /** @type {MediaRecorder|null} */ (null),
  chunks: /** @type {BlobPart[]} */ ([]),
  lastBlob: /** @type {Blob|null} */ (null),
  lastUrl: /** @type {string|null} */ (null),
  lastMimeType: /** @type {string|null} */ (null),

  recordings: /** @type {{id: string, createdAt: number, blob: Blob, url: string, mimeType: string}[]} */ ([]),

  timerInterval: /** @type {number|null} */ (null),
  recordStartedAt: /** @type {number|null} */ (null),

  monitorEnabled: false,
  monitorAudioEl: /** @type {HTMLAudioElement|null} */ (null),
};

function pad2(n) {
  return String(n).padStart(2, "0");
}

function formatMs(ms) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${pad2(m)}:${pad2(s)}`;
}

function bytesToHuman(bytes) {
  const units = ["B", "KB", "MB", "GB"];
  let val = bytes;
  let i = 0;
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024;
    i += 1;
  }
  const digits = i === 0 ? 0 : i === 1 ? 1 : 2;
  return `${val.toFixed(digits)} ${units[i]}`;
}

function setStatus(stateKey, text) {
  els.statusPill.dataset.state = stateKey;
  els.statusText.textContent = text;
}

function setTimerText(ms) {
  els.timerText.textContent = formatMs(ms);
}

function startTimer() {
  stopTimer();
  state.recordStartedAt = Date.now();
  setTimerText(0);

  state.timerInterval = window.setInterval(() => {
    if (!state.recordStartedAt) return;
    setTimerText(Date.now() - state.recordStartedAt);
  }, 250);
}

function stopTimer() {
  if (state.timerInterval) {
    window.clearInterval(state.timerInterval);
    state.timerInterval = null;
  }
  state.recordStartedAt = null;
  setTimerText(0);
}

function cleanupLastUrl() {
  if (state.lastUrl) {
    URL.revokeObjectURL(state.lastUrl);
    state.lastUrl = null;
  }
}

/**
 * Choose a mimeType that is likely to be supported by MediaRecorder in this browser.
 * We keep it simple and prefer webm/opus.
 */
function pickMimeType() {
  const candidates = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/ogg;codecs=opus",
    "audio/ogg",
  ];

  for (const c of candidates) {
    if (window.MediaRecorder && MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(c)) {
      return c;
    }
  }
  return ""; // Let browser decide
}

function setControls({
  canStart,
  canStop,
  canSave,
  canMonitor,
  monitorEnabled,
}) {
  els.btnStart.disabled = !canStart;
  els.btnStop.disabled = !canStop;
  els.btnSave.disabled = !canSave;
  els.btnToggleMonitor.disabled = !canMonitor;
  els.btnToggleMonitor.textContent = monitorEnabled ? "Disable monitor" : "Enable monitor";
}

function updateSessionSummary() {
  const n = state.recordings.length;
  els.sessionSummary.textContent = `${n} recording${n === 1 ? "" : "s"}`;
  els.recordingsEmpty.style.display = n === 0 ? "block" : "none";
}

function renderRecordings() {
  els.recordingsList.innerHTML = "";

  for (const rec of state.recordings) {
    const node = /** @type {HTMLElement} */ (template.content.firstElementChild.cloneNode(true));

    const title = node.querySelector(".rec-title");
    const meta = node.querySelector(".rec-meta");
    const audio = /** @type {HTMLAudioElement} */ (node.querySelector(".rec-audio"));
    const btnDownload = /** @type {HTMLButtonElement} */ (node.querySelector(".rec-download"));
    const btnDelete = /** @type {HTMLButtonElement} */ (node.querySelector(".rec-delete"));

    const dt = new Date(rec.createdAt);
    title.textContent = `Recording ${dt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;

    meta.textContent = `${dt.toLocaleDateString()} • ${rec.mimeType || "audio"} • ${bytesToHuman(rec.blob.size)}`;

    audio.src = rec.url;

    btnDownload.addEventListener("click", () => {
      const ext = rec.mimeType.includes("ogg") ? "ogg" : "webm";
      const a = document.createElement("a");
      a.href = rec.url;
      a.download = `ocean-rec-${rec.id}.${ext}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
    });

    btnDelete.addEventListener("click", () => {
      const idx = state.recordings.findIndex((r) => r.id === rec.id);
      if (idx >= 0) {
        const [removed] = state.recordings.splice(idx, 1);
        URL.revokeObjectURL(removed.url);
        renderRecordings();
        updateSessionSummary();
      }
    });

    els.recordingsList.appendChild(node);
  }
}

function setPreview(blob, url, mimeType) {
  els.audioPreview.src = url;
  const metaText = blob
    ? `${mimeType || "audio"} • ${bytesToHuman(blob.size)}`
    : "No recording yet";
  els.previewMeta.textContent = metaText;
}

async function ensureStream() {
  if (state.stream) return state.stream;

  // Prompt the user for microphone access.
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  state.stream = stream;
  return stream;
}

function stopStreamTracks() {
  if (!state.stream) return;
  for (const t of state.stream.getTracks()) t.stop();
  state.stream = null;
}

function enableMonitor(stream) {
  if (state.monitorAudioEl) return;

  // Audio monitoring via element + stream (simple and widely supported).
  const a = document.createElement("audio");
  a.autoplay = true;
  a.muted = false;
  a.volume = 1;
  a.srcObject = stream;

  // Keep hidden but in DOM for some browsers.
  a.style.position = "fixed";
  a.style.left = "-9999px";
  a.style.top = "0";
  document.body.appendChild(a);

  state.monitorAudioEl = a;
}

function disableMonitor() {
  if (!state.monitorAudioEl) return;
  state.monitorAudioEl.pause();
  state.monitorAudioEl.srcObject = null;
  state.monitorAudioEl.remove();
  state.monitorAudioEl = null;
}

async function startRecording() {
  if (!state.supported) {
    setStatus("idle", "Unsupported");
    alert("MediaRecorder is not supported in this browser.");
    return;
  }

  // Cleanup last preview URL so it doesn't leak.
  cleanupLastUrl();

  setStatus("recording", "Recording");
  setControls({
    canStart: false,
    canStop: true,
    canSave: false,
    canMonitor: true,
    monitorEnabled: state.monitorEnabled,
  });

  state.chunks = [];
  state.lastBlob = null;
  state.lastMimeType = null;

  const stream = await ensureStream();

  if (state.monitorEnabled) enableMonitor(stream);
  else disableMonitor();

  const mimeType = pickMimeType();
  state.lastMimeType = mimeType || null;

  const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
  state.mediaRecorder = recorder;

  recorder.addEventListener("dataavailable", (e) => {
    if (e.data && e.data.size > 0) state.chunks.push(e.data);
  });

  recorder.addEventListener("stop", () => {
    const finalType = recorder.mimeType || state.lastMimeType || "";
    const blob = new Blob(state.chunks, { type: finalType || undefined });
    const url = URL.createObjectURL(blob);

    state.lastBlob = blob;
    state.lastUrl = url;
    state.lastMimeType = finalType || state.lastMimeType;

    setPreview(blob, url, state.lastMimeType || "");

    setStatus("ready", "Ready");
    stopTimer();

    setControls({
      canStart: true,
      canStop: false,
      canSave: true,
      canMonitor: true,
      monitorEnabled: state.monitorEnabled,
    });
  });

  recorder.start();
  startTimer();
}

function stopRecording() {
  if (!state.mediaRecorder) return;

  if (state.mediaRecorder.state !== "inactive") {
    state.mediaRecorder.stop();
  }
}

function saveRecordingToSession() {
  if (!state.lastBlob || !state.lastUrl) return;

  // Create a dedicated URL per saved recording (so preview can be replaced later without breaking the list).
  const blob = state.lastBlob;
  const url = URL.createObjectURL(blob);
  const id = crypto && "randomUUID" in crypto ? crypto.randomUUID() : String(Date.now());

  state.recordings.unshift({
    id,
    createdAt: Date.now(),
    blob,
    url,
    mimeType: state.lastMimeType || blob.type || "",
  });

  renderRecordings();
  updateSessionSummary();

  // After saving, keep preview as-is but disable "Save" to avoid duplicates without a new recording.
  setControls({
    canStart: true,
    canStop: false,
    canSave: false,
    canMonitor: true,
    monitorEnabled: state.monitorEnabled,
  });
}

function clearAllRecordings() {
  for (const r of state.recordings) URL.revokeObjectURL(r.url);
  state.recordings = [];
  renderRecordings();
  updateSessionSummary();
}

function toggleMonitor() {
  state.monitorEnabled = !state.monitorEnabled;

  if (state.stream && state.mediaRecorder && state.mediaRecorder.state !== "inactive") {
    if (state.monitorEnabled) enableMonitor(state.stream);
    else disableMonitor();
  }

  setControls({
    canStart: !state.mediaRecorder || state.mediaRecorder.state === "inactive",
    canStop: !!state.mediaRecorder && state.mediaRecorder.state !== "inactive",
    canSave: !!state.lastBlob && (!state.mediaRecorder || state.mediaRecorder.state === "inactive") && els.btnSave.disabled === false ? true : !els.btnSave.disabled,
    canMonitor: true,
    monitorEnabled: state.monitorEnabled,
  });
}

function init() {
  updateSessionSummary();
  setStatus("idle", "Idle");
  setPreview(null, "", "");

  if (!state.supported || !navigator.mediaDevices?.getUserMedia) {
    setStatus("idle", "Unsupported");
    els.btnStart.disabled = true;
    els.btnStop.disabled = true;
    els.btnSave.disabled = true;
    els.btnToggleMonitor.disabled = true;
    document.getElementById("supportNote").innerHTML =
      "<strong>Unsupported:</strong> This browser does not support MediaRecorder or microphone capture.";
    return;
  }

  setControls({
    canStart: true,
    canStop: false,
    canSave: false,
    canMonitor: false,
    monitorEnabled: state.monitorEnabled,
  });

  els.btnStart.addEventListener("click", () => startRecording().catch((err) => {
    console.error(err);
    setStatus("idle", "Idle");
    stopTimer();
    setControls({
      canStart: true,
      canStop: false,
      canSave: false,
      canMonitor: false,
      monitorEnabled: state.monitorEnabled,
    });
    alert(`Could not start recording. ${err?.message || err}`);
  }));

  els.btnStop.addEventListener("click", () => {
    setStatus("recording", "Stopping…");
    stopRecording();
  });

  els.btnSave.addEventListener("click", () => saveRecordingToSession());

  els.btnClearAll.addEventListener("click", () => clearAllRecordings());

  els.btnToggleMonitor.addEventListener("click", () => toggleMonitor());

  // Cleanup resources on navigation away.
  window.addEventListener("beforeunload", () => {
    cleanupLastUrl();
    clearAllRecordings();
    disableMonitor();
    stopStreamTracks();
  });
}

init();
