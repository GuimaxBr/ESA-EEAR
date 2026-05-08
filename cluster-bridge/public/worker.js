const workerForm = document.getElementById("worker-form");
const workerDashboard = document.getElementById("worker-dashboard");
const workerTitle = document.getElementById("worker-title");
const workerSubtitle = document.getElementById("worker-subtitle");
const workerLoad = document.getElementById("worker-load");
const workerCompleted = document.getElementById("worker-completed");
const workerLastMs = document.getElementById("worker-last-ms");
const workerLog = document.getElementById("worker-log");
const deviceNameInput = document.getElementById("device-name");

let deviceId = localStorage.getItem("cluster_worker_device_id") || "";
let connected = false;
let completedChunks = 0;
let heartbeatTimer = null;
let pollTimer = null;

function log(message, level = "info") {
  const badgeClass = level === "error" ? "danger" : level === "ok" ? "ok" : "warn";
  const item = document.createElement("article");
  item.className = "list-card";
  item.innerHTML = `
    <div class="list-row">
      <strong>${escapeHtml(message)}</strong>
      <span class="badge ${badgeClass}">${level}</span>
    </div>
    <div class="list-row">
      <span class="muted">${new Date().toLocaleTimeString("pt-BR")}</span>
    </div>
  `;
  workerLog.prepend(item);
  while (workerLog.childElementCount > 12) {
    workerLog.removeChild(workerLog.lastElementChild);
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function api(path, body) {
  const response = await fetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({ error: "Erro inesperado." }));
    throw new Error(payload.error || "Erro inesperado.");
  }
  return response.json();
}

function setConnectedState(isConnected, name = "") {
  connected = isConnected;
  workerForm.parentElement.hidden = isConnected;
  workerDashboard.hidden = !isConnected;
  if (isConnected) {
    workerTitle.textContent = name;
    workerSubtitle.textContent = "Aguardando tarefa do cluster.";
  }
}

async function registerWorker(name) {
  const payload = await api("/api/worker/register", {
    deviceId,
    deviceName: name,
    estimatedLoadPercent: 0
  });
  deviceId = payload.deviceId;
  localStorage.setItem("cluster_worker_device_id", deviceId);
  setConnectedState(true, name);
  log(`Worker ${name} conectado.`, "ok");
  startLoop();
}

async function sendHeartbeat(estimatedLoadPercent = 0) {
  if (!deviceId) return;
  await api("/api/worker/heartbeat", {
    deviceId,
    estimatedLoadPercent
  });
}

function startLoop() {
  stopLoop();
  heartbeatTimer = window.setInterval(() => {
    sendHeartbeat(0).catch((error) => log(error.message, "error"));
  }, 5000);
  pollTimer = window.setInterval(() => {
    pollTask().catch((error) => log(error.message, "error"));
  }, 1700);
  pollTask().catch((error) => log(error.message, "error"));
}

function stopLoop() {
  if (heartbeatTimer) window.clearInterval(heartbeatTimer);
  if (pollTimer) window.clearInterval(pollTimer);
  heartbeatTimer = null;
  pollTimer = null;
}

async function pollTask() {
  if (!connected) return;
  const payload = await api("/api/worker/poll", { deviceId });
  if (!payload.assignment) {
    return;
  }
  const assignment = payload.assignment;
  workerSubtitle.textContent = `Processando ${assignment.fileName} - parte ${assignment.index + 1}`;
  workerLoad.textContent = `${assignment.targetLoadPercent}%`;
  log(`Chunk ${assignment.index + 1} recebido de ${assignment.fileName}.`, "warn");
  const startedAt = performance.now();
  try {
    const input = base64ToBytes(assignment.inputBase64);
    const compressed = await gzipBytes(input);
    const processingMs = Math.round(performance.now() - startedAt);
    const digestHex = await sha256Hex(input);
    await api("/api/worker/result", {
      deviceId,
      jobId: assignment.jobId,
      chunkId: assignment.chunkId,
      status: "completed",
      resultBase64: bytesToBase64(compressed),
      outputBytes: compressed.length,
      processingMs,
      sha256: digestHex,
      estimatedLoadPercent: assignment.targetLoadPercent
    });
    completedChunks += 1;
    workerCompleted.textContent = String(completedChunks);
    workerLastMs.textContent = `${processingMs} ms`;
    workerSubtitle.textContent = "Aguardando proxima tarefa.";
    log(`Chunk ${assignment.index + 1} concluido em ${processingMs} ms.`, "ok");
  } catch (error) {
    await api("/api/worker/result", {
      deviceId,
      jobId: assignment.jobId,
      chunkId: assignment.chunkId,
      status: "failed",
      errorMessage: error.message,
      estimatedLoadPercent: 0
    });
    workerSubtitle.textContent = "Falha na tarefa; chunk devolvido para a fila.";
    log(`Falha ao processar chunk: ${error.message}`, "error");
  }
}

async function gzipBytes(bytes) {
  if (!("CompressionStream" in window)) {
    throw new Error("Este navegador nao suporta CompressionStream.");
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream("gzip"));
  const arrayBuffer = await new Response(stream).arrayBuffer();
  return new Uint8Array(arrayBuffer);
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}

function base64ToBytes(base64) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function bytesToBase64(bytes) {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

workerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const deviceName = deviceNameInput.value.trim();
  if (!deviceName) return;
  await registerWorker(deviceName);
});

if (deviceId) {
  deviceNameInput.value = localStorage.getItem("cluster_worker_device_name") || "";
}

deviceNameInput.addEventListener("change", () => {
  localStorage.setItem("cluster_worker_device_name", deviceNameInput.value.trim());
});
