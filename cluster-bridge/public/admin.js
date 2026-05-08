const loginPanel = document.getElementById("login-panel");
const dashboard = document.getElementById("dashboard");
const loginForm = document.getElementById("login-form");
const loginError = document.getElementById("login-error");
const logoutButton = document.getElementById("logout-button");
const configForm = document.getElementById("config-form");
const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("file-input");
const chooseFileButton = document.getElementById("choose-file-button");
const uploadStatus = document.getElementById("upload-status");
const devicesList = document.getElementById("devices-list");
const jobsList = document.getElementById("jobs-list");
const eventsList = document.getElementById("events-list");

const stateEls = {
  online: document.getElementById("stat-online"),
  queued: document.getElementById("stat-queued"),
  leased: document.getElementById("stat-leased"),
  completed: document.getElementById("stat-completed"),
  publicBaseUrl: document.getElementById("public-base-url"),
  workerMaxLoad: document.getElementById("worker-max-load"),
  chunkSize: document.getElementById("chunk-size"),
  staleLease: document.getElementById("stale-lease")
};

let pollHandle = null;

async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  if (!response.ok) {
    let message = "Erro inesperado.";
    try {
      const payload = await response.json();
      message = payload.error || message;
    } catch {
      message = response.statusText || message;
    }
    throw new Error(message);
  }
  const type = response.headers.get("Content-Type") || "";
  if (type.includes("application/json")) {
    return response.json();
  }
  return response;
}

function setLoggedIn(loggedIn) {
  loginPanel.hidden = loggedIn;
  dashboard.hidden = !loggedIn;
  if (loggedIn && !pollHandle) {
    refreshState();
    pollHandle = window.setInterval(refreshState, 2500);
  }
  if (!loggedIn && pollHandle) {
    window.clearInterval(pollHandle);
    pollHandle = null;
  }
}

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString("pt-BR");
}

function jobProgress(job) {
  const total = job.chunks.length || 1;
  const completed = job.chunks.filter((chunk) => chunk.status === "completed").length;
  return Math.round((completed / total) * 100);
}

function renderDevices(devices) {
  if (!devices.length) {
    devicesList.innerHTML = `<div class="list-card"><p class="muted">Nenhum dispositivo conectado ainda.</p></div>`;
    return;
  }
  devicesList.innerHTML = devices
    .map((device) => {
      const badge = device.online
        ? `<span class="badge ok">online</span>`
        : `<span class="badge warn">offline</span>`;
      const load = Math.max(0, Number(device.estimatedLoadPercent || 0));
      return `
        <article class="list-card">
          <div class="list-row">
            <strong>${escapeHtml(device.name)}</strong>
            ${badge}
          </div>
          <div class="list-row">
            <span class="muted">Carga estimada</span>
            <span>${load}% / ${device.configuredLoadPercent}%</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" style="width:${Math.min(load, 100)}%"></div>
          </div>
          <div class="list-row">
            <span class="muted">Ultimo ping</span>
            <span>${formatDate(device.lastSeenAt)}</span>
          </div>
          <div class="list-row">
            <span class="muted">Chunks concluidos</span>
            <span>${device.completedChunks}</span>
          </div>
          <div class="list-row">
            <span class="muted">Tempo medio</span>
            <span>${device.averageProcessingMs || 0} ms</span>
          </div>
        </article>
      `;
    })
    .join("");
}

function renderJobs(jobs) {
  if (!jobs.length) {
    jobsList.innerHTML = `<div class="list-card"><p class="muted">Nenhuma tarefa na fila.</p></div>`;
    return;
  }
  jobsList.innerHTML = jobs
    .map((job) => {
      const progress = jobProgress(job);
      const completed = job.chunks.filter((chunk) => chunk.status === "completed").length;
      const queued = job.chunks.filter((chunk) => chunk.status === "queued").length;
      const leased = job.chunks.filter((chunk) => chunk.status === "leased").length;
      const statusClass =
        job.status === "completed" ? "ok" : leased > 0 ? "warn" : "badge";
      const download = job.status === "completed"
        ? `<a class="secondary-link" href="/api/admin/tasks/${job.id}/download">Baixar resultado</a>`
        : "";
      return `
        <article class="list-card">
          <div class="list-row">
            <strong>${escapeHtml(job.fileName)}</strong>
            <span class="badge ${statusClass}">${escapeHtml(job.status)}</span>
          </div>
          <div class="list-row">
            <span class="muted">Progresso</span>
            <span>${completed}/${job.chunks.length} chunks</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" style="width:${progress}%"></div>
          </div>
          <div class="list-row">
            <span class="muted">Na fila</span>
            <span>${queued}</span>
          </div>
          <div class="list-row">
            <span class="muted">Em execucao</span>
            <span>${leased}</span>
          </div>
          <div class="list-row">
            <span class="muted">Criado em</span>
            <span>${formatDate(job.createdAt)}</span>
          </div>
          ${download ? `<div class="list-row"><span></span>${download}</div>` : ""}
        </article>
      `;
    })
    .join("");
}

function renderEvents(events) {
  if (!events.length) {
    eventsList.innerHTML = `<div class="list-card"><p class="muted">Sem eventos recentes.</p></div>`;
    return;
  }
  eventsList.innerHTML = events
    .slice(0, 20)
    .map(
      (event) => `
        <article class="list-card">
          <div class="list-row">
            <strong>${escapeHtml(event.message)}</strong>
            <span class="muted">${formatDate(event.createdAt)}</span>
          </div>
          <div class="list-row">
            <span class="badge">${escapeHtml(event.type)}</span>
          </div>
        </article>
      `
    )
    .join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function refreshState() {
  try {
    const state = await api("/api/admin/state", { method: "GET" });
    stateEls.online.textContent = state.summary.onlineDevices;
    stateEls.queued.textContent = state.summary.queuedChunks;
    stateEls.leased.textContent = state.summary.leasedChunks;
    stateEls.completed.textContent = state.summary.completedJobs;
    stateEls.publicBaseUrl.value = state.config.publicBaseUrl || "";
    stateEls.workerMaxLoad.value = state.config.workerMaxLoadPercent;
    stateEls.chunkSize.value = state.config.chunkSizeBytes;
    stateEls.staleLease.value = state.config.staleLeaseMs;
    renderDevices(state.devices);
    renderJobs(state.jobs);
    renderEvents(state.events);
    setLoggedIn(true);
  } catch (error) {
    if (String(error.message).includes("Nao autorizado")) {
      setLoggedIn(false);
      return;
    }
    console.error(error);
  }
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginError.hidden = true;
  try {
    await api("/api/admin/login", {
      method: "POST",
      body: JSON.stringify({
        password: document.getElementById("admin-password").value
      })
    });
    setLoggedIn(true);
    await refreshState();
  } catch (error) {
    loginError.hidden = false;
    loginError.textContent = error.message;
  }
});

logoutButton.addEventListener("click", async () => {
  await api("/api/admin/logout", { method: "POST" });
  setLoggedIn(false);
});

configForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await api("/api/admin/config", {
    method: "POST",
    body: JSON.stringify({
      publicBaseUrl: stateEls.publicBaseUrl.value,
      workerMaxLoadPercent: Number(stateEls.workerMaxLoad.value),
      chunkSizeBytes: Number(stateEls.chunkSize.value),
      staleLeaseMs: Number(stateEls.staleLease.value)
    })
  });
  await refreshState();
});

async function uploadFile(file) {
  uploadStatus.textContent = `Enviando ${file.name}...`;
  const arrayBuffer = await file.arrayBuffer();
  const payloadBase64 = bytesToBase64(new Uint8Array(arrayBuffer));
  const result = await api("/api/admin/tasks", {
    method: "POST",
    body: JSON.stringify({
      fileName: file.name,
      mimeType: file.type || "application/octet-stream",
      payloadBase64
    })
  });
  uploadStatus.textContent = `Tarefa criada com ${result.chunkCount} chunks.`;
  await refreshState();
}

function bytesToBase64(bytes) {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

dropzone.addEventListener("dragover", (event) => {
  event.preventDefault();
  dropzone.classList.add("dragging");
});

dropzone.addEventListener("dragleave", () => {
  dropzone.classList.remove("dragging");
});

dropzone.addEventListener("drop", async (event) => {
  event.preventDefault();
  dropzone.classList.remove("dragging");
  const [file] = event.dataTransfer.files;
  if (file) {
    await uploadFile(file);
  }
});

dropzone.addEventListener("click", () => fileInput.click());
chooseFileButton.addEventListener("click", () => fileInput.click());
fileInput.addEventListener("change", async (event) => {
  const [file] = event.target.files;
  if (file) {
    await uploadFile(file);
  }
  fileInput.value = "";
});

refreshState();
