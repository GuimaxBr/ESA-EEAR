import { createServer } from "node:http";
import { randomUUID, createHash, randomBytes } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "..");
const PUBLIC_DIR = path.join(ROOT_DIR, "public");
const DATA_DIR = path.join(ROOT_DIR, "data");
const STATE_FILE = path.join(DATA_DIR, "state.json");

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";
const ADMIN_PASSWORD = process.env.CLUSTER_ADMIN_PASSWORD || "troque-esta-senha";

const sessions = new Map();
let writeQueue = Promise.resolve();

function nowIso() {
  return new Date().toISOString();
}

function defaultState() {
  return {
    config: {
      publicBaseUrl: `http://localhost:${PORT}`,
      workerMaxLoadPercent: 50,
      chunkSizeBytes: 256 * 1024,
      staleLeaseMs: 30_000,
      workerOfflineMs: 15_000,
      workerPollMs: 1_500,
      maxConcurrentChunksPerDevice: 1,
      updatedAt: nowIso()
    },
    devices: {},
    jobs: {},
    events: []
  };
}

async function ensureStateFile() {
  await fs.mkdir(DATA_DIR, { recursive: true });
  try {
    await fs.access(STATE_FILE);
  } catch {
    await fs.writeFile(STATE_FILE, JSON.stringify(defaultState(), null, 2), "utf8");
  }
}

async function loadState() {
  await ensureStateFile();
  const raw = await fs.readFile(STATE_FILE, "utf8");
  const parsed = JSON.parse(raw);
  return {
    ...defaultState(),
    ...parsed,
    config: {
      ...defaultState().config,
      ...(parsed.config || {})
    },
    devices: parsed.devices || {},
    jobs: parsed.jobs || {},
    events: parsed.events || []
  };
}

function saveState(state) {
  writeQueue = writeQueue.then(async () => {
    state.events = state.events.slice(-200);
    await fs.mkdir(DATA_DIR, { recursive: true });
    await fs.writeFile(STATE_FILE, JSON.stringify(state, null, 2), "utf8");
  });
  return writeQueue;
}

function appendEvent(state, type, message, extra = {}) {
  state.events.push({
    id: randomUUID(),
    type,
    message,
    createdAt: nowIso(),
    ...extra
  });
}

function readCookie(request, name) {
  const cookieHeader = request.headers.cookie || "";
  const cookies = cookieHeader.split(";").map((part) => part.trim());
  for (const cookie of cookies) {
    const [cookieName, ...rest] = cookie.split("=");
    if (cookieName === name) {
      return decodeURIComponent(rest.join("="));
    }
  }
  return "";
}

function createSession() {
  const token = randomBytes(32).toString("hex");
  const expiresAt = Date.now() + 12 * 60 * 60 * 1000;
  sessions.set(token, { expiresAt });
  return { token, expiresAt };
}

function isAuthenticated(request) {
  const token = readCookie(request, "cluster_admin_session");
  if (!token) {
    return false;
  }
  const session = sessions.get(token);
  if (!session) {
    return false;
  }
  if (session.expiresAt < Date.now()) {
    sessions.delete(token);
    return false;
  }
  return true;
}

function unauthorized(response) {
  return sendJson(response, 401, { error: "Nao autorizado." });
}

function notFound(response) {
  return sendJson(response, 404, { error: "Rota nao encontrada." });
}

function sendJson(response, statusCode, payload, headers = {}) {
  response.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    ...headers
  });
  response.end(JSON.stringify(payload));
}

function sendText(response, statusCode, body, headers = {}) {
  response.writeHead(statusCode, {
    "Content-Type": "text/plain; charset=utf-8",
    ...headers
  });
  response.end(body);
}

function safePublicPath(urlPath) {
  const cleaned = urlPath === "/" ? "/index.html" : urlPath;
  const normalized = path.normalize(cleaned).replace(/^(\.\.[/\\])+/, "");
  return path.join(PUBLIC_DIR, normalized);
}

function contentTypeFor(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  switch (ext) {
    case ".html":
      return "text/html; charset=utf-8";
    case ".css":
      return "text/css; charset=utf-8";
    case ".js":
      return "application/javascript; charset=utf-8";
    case ".json":
      return "application/json; charset=utf-8";
    default:
      return "application/octet-stream";
  }
}

async function serveStatic(request, response) {
  try {
    const filePath = safePublicPath(new URL(request.url, `http://${request.headers.host}`).pathname);
    if (!filePath.startsWith(PUBLIC_DIR)) {
      sendText(response, 403, "Acesso negado.");
      return;
    }
    const data = await fs.readFile(filePath);
    response.writeHead(200, { "Content-Type": contentTypeFor(filePath) });
    response.end(data);
  } catch {
    sendText(response, 404, "Arquivo nao encontrado.");
  }
}

async function readJsonBody(request) {
  return new Promise((resolve, reject) => {
    let raw = "";
    request.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 30 * 1024 * 1024) {
        reject(new Error("Payload grande demais."));
        request.destroy();
      }
    });
    request.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(error);
      }
    });
    request.on("error", reject);
  });
}

function base64ToBuffer(base64) {
  return Buffer.from(base64, "base64");
}

function chunkBuffer(buffer, chunkSizeBytes) {
  const chunks = [];
  for (let offset = 0; offset < buffer.length; offset += chunkSizeBytes) {
    chunks.push(buffer.subarray(offset, Math.min(offset + chunkSizeBytes, buffer.length)));
  }
  return chunks;
}

function reclaimStaleChunks(state) {
  const now = Date.now();
  for (const job of Object.values(state.jobs)) {
    let hasQueued = false;
    let hasLeased = false;
    let completedCount = 0;
    for (const chunk of job.chunks) {
      if (chunk.status === "leased" && chunk.leaseExpiresAt && chunk.leaseExpiresAt < now) {
        chunk.status = "queued";
        chunk.assignedDeviceId = "";
        chunk.leaseExpiresAt = 0;
        chunk.lastError = "Lease expirado; chunk devolvido para a fila.";
        appendEvent(state, "chunk-requeued", `Chunk ${chunk.id} da tarefa ${job.id} voltou para a fila.`, {
          jobId: job.id,
          chunkId: chunk.id
        });
      }
      if (chunk.status === "queued") {
        hasQueued = true;
      }
      if (chunk.status === "leased") {
        hasLeased = true;
      }
      if (chunk.status === "completed") {
        completedCount += 1;
      }
    }
    if (completedCount === job.chunks.length && job.chunks.length > 0) {
      job.status = "completed";
      job.completedAt = job.completedAt || nowIso();
    } else if (hasLeased || hasQueued) {
      job.status = "running";
    }
  }
}

function summarizeState(state) {
  const now = Date.now();
  const devices = Object.values(state.devices)
    .sort((a, b) => (b.lastSeenAt || "").localeCompare(a.lastSeenAt || ""));
  const jobs = Object.values(state.jobs)
    .sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));

  const deviceSummaries = devices.map((device) => {
    const lastSeenMillis = device.lastSeenAt ? Date.parse(device.lastSeenAt) : 0;
    const online = now - lastSeenMillis <= state.config.workerOfflineMs;
    return {
      ...device,
      online
    };
  });

  return {
    config: state.config,
    summary: {
      totalDevices: deviceSummaries.length,
      onlineDevices: deviceSummaries.filter((item) => item.online).length,
      queuedChunks: jobs.reduce((sum, job) => sum + job.chunks.filter((chunk) => chunk.status === "queued").length, 0),
      leasedChunks: jobs.reduce((sum, job) => sum + job.chunks.filter((chunk) => chunk.status === "leased").length, 0),
      completedJobs: jobs.filter((job) => job.status === "completed").length
    },
    devices: deviceSummaries,
    jobs,
    events: [...state.events].sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""))
  };
}

function nextQueuedChunk(state, deviceId) {
  const device = state.devices[deviceId];
  if (!device) {
    return null;
  }
  const activeChunks = Object.values(state.jobs).flatMap((job) =>
    job.chunks.filter((chunk) => chunk.assignedDeviceId === deviceId && chunk.status === "leased")
  );
  if (activeChunks.length >= state.config.maxConcurrentChunksPerDevice) {
    return null;
  }

  const jobs = Object.values(state.jobs).sort((a, b) => (a.createdAt || "").localeCompare(b.createdAt || ""));
  for (const job of jobs) {
    if (job.status === "completed") {
      continue;
    }
    const chunk = job.chunks.find((item) => item.status === "queued");
    if (chunk) {
      chunk.status = "leased";
      chunk.assignedDeviceId = deviceId;
      chunk.leaseExpiresAt = Date.now() + state.config.staleLeaseMs;
      chunk.attempts = (chunk.attempts || 0) + 1;
      chunk.lastStartedAt = nowIso();
      device.currentJobId = job.id;
      device.currentChunkId = chunk.id;
      device.lastAssignedAt = nowIso();
      job.status = "running";
      appendEvent(state, "chunk-assigned", `Chunk ${chunk.id} foi atribuido ao dispositivo ${device.name}.`, {
        jobId: job.id,
        chunkId: chunk.id,
        deviceId
      });
      return { job, chunk };
    }
  }
  return null;
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let i = 0; i < 8; i += 1) {
      const mask = -(crc & 1);
      crc = (crc >>> 1) ^ (0xedb88320 & mask);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function buildStoredZip(entries) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  for (const entry of entries) {
    const nameBuffer = Buffer.from(entry.name, "utf8");
    const data = entry.data;
    const crc = crc32(data);
    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4);
    localHeader.writeUInt16LE(0, 6);
    localHeader.writeUInt16LE(0, 8);
    localHeader.writeUInt16LE(0, 10);
    localHeader.writeUInt16LE(0, 12);
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(data.length, 18);
    localHeader.writeUInt32LE(data.length, 22);
    localHeader.writeUInt16LE(nameBuffer.length, 26);
    localHeader.writeUInt16LE(0, 28);
    localParts.push(localHeader, nameBuffer, data);

    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0, 8);
    centralHeader.writeUInt16LE(0, 10);
    centralHeader.writeUInt16LE(0, 12);
    centralHeader.writeUInt16LE(0, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(data.length, 20);
    centralHeader.writeUInt32LE(data.length, 24);
    centralHeader.writeUInt16LE(nameBuffer.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE(0, 38);
    centralHeader.writeUInt32LE(offset, 42);
    centralParts.push(centralHeader, nameBuffer);

    offset += localHeader.length + nameBuffer.length + data.length;
  }

  const centralDirectory = Buffer.concat(centralParts);
  const endRecord = Buffer.alloc(22);
  endRecord.writeUInt32LE(0x06054b50, 0);
  endRecord.writeUInt16LE(0, 4);
  endRecord.writeUInt16LE(0, 6);
  endRecord.writeUInt16LE(entries.length, 8);
  endRecord.writeUInt16LE(entries.length, 10);
  endRecord.writeUInt32LE(centralDirectory.length, 12);
  endRecord.writeUInt32LE(offset, 16);
  endRecord.writeUInt16LE(0, 20);

  return Buffer.concat([...localParts, centralDirectory, endRecord]);
}

function finalizeJobArchive(job) {
  if (job.status !== "completed") {
    return;
  }
  const entries = [];
  const manifest = {
    jobId: job.id,
    originalFileName: job.fileName,
    mimeType: job.mimeType,
    createdAt: job.createdAt,
    completedAt: job.completedAt,
    chunkCount: job.chunks.length,
    chunkSizeBytes: job.chunkSizeBytes,
    algorithm: "gzip-per-chunk",
    note: "Cada parte .gz deve ser descompactada e concatenada na ordem para remontar o arquivo original.",
    parts: job.chunks
      .sort((a, b) => a.index - b.index)
      .map((chunk) => ({
        index: chunk.index,
        fileName: `${job.safeBaseName}.part-${String(chunk.index).padStart(4, "0")}.gz`,
        inputBytes: chunk.inputBytes,
        outputBytes: chunk.outputBytes,
        sha256: chunk.sha256
      }))
  };
  entries.push({
    name: "manifest.json",
    data: Buffer.from(JSON.stringify(manifest, null, 2), "utf8")
  });
  for (const chunk of [...job.chunks].sort((a, b) => a.index - b.index)) {
    entries.push({
      name: `${job.safeBaseName}.part-${String(chunk.index).padStart(4, "0")}.gz`,
      data: Buffer.from(chunk.resultBase64, "base64")
    });
  }
  const archive = buildStoredZip(entries);
  job.outputArchiveName = `${job.safeBaseName}.clusterzip.zip`;
  job.outputArchiveBase64 = archive.toString("base64");
}

function sanitizeFileName(fileName) {
  return fileName.replace(/[^a-zA-Z0-9._-]+/g, "_");
}

async function handleAdminLogin(request, response) {
  const body = await readJsonBody(request);
  const passwordHash = createHash("sha256").update(String(body.password || "")).digest("hex");
  const expectedHash = createHash("sha256").update(ADMIN_PASSWORD).digest("hex");
  if (passwordHash !== expectedHash) {
    sendJson(response, 401, { error: "Senha incorreta." });
    return;
  }
  const session = createSession();
  sendJson(
    response,
    200,
    { ok: true },
    {
      "Set-Cookie": `cluster_admin_session=${session.token}; HttpOnly; SameSite=Lax; Path=/; Max-Age=43200`
    }
  );
}

async function handleAdminLogout(response) {
  sendJson(
    response,
    200,
    { ok: true },
    {
      "Set-Cookie": "cluster_admin_session=; HttpOnly; SameSite=Lax; Path=/; Max-Age=0"
    }
  );
}

async function handleAdminState(response) {
  const state = await loadState();
  reclaimStaleChunks(state);
  await saveState(state);
  sendJson(response, 200, summarizeState(state));
}

async function handleAdminConfig(request, response) {
  const body = await readJsonBody(request);
  const state = await loadState();
  state.config.publicBaseUrl = String(body.publicBaseUrl || state.config.publicBaseUrl).trim();
  state.config.workerMaxLoadPercent = Number(body.workerMaxLoadPercent || state.config.workerMaxLoadPercent);
  state.config.chunkSizeBytes = Number(body.chunkSizeBytes || state.config.chunkSizeBytes);
  state.config.staleLeaseMs = Number(body.staleLeaseMs || state.config.staleLeaseMs);
  state.config.updatedAt = nowIso();
  appendEvent(state, "config-updated", "Configuracao do cluster atualizada.");
  await saveState(state);
  sendJson(response, 200, { ok: true, config: state.config });
}

async function handleAdminCreateTask(request, response) {
  const body = await readJsonBody(request);
  const fileName = String(body.fileName || "arquivo.bin").trim() || "arquivo.bin";
  const mimeType = String(body.mimeType || "application/octet-stream");
  const payloadBase64 = String(body.payloadBase64 || "");
  if (!payloadBase64) {
    sendJson(response, 400, { error: "Arquivo ausente." });
    return;
  }
  const state = await loadState();
  const buffer = base64ToBuffer(payloadBase64);
  const chunkSizeBytes = Number(body.chunkSizeBytes || state.config.chunkSizeBytes);
  const safeBaseName = sanitizeFileName(path.parse(fileName).name || "arquivo");
  const chunks = chunkBuffer(buffer, chunkSizeBytes).map((chunkBufferPart, index) => ({
    id: randomUUID(),
    index,
    status: "queued",
    attempts: 0,
    assignedDeviceId: "",
    leaseExpiresAt: 0,
    inputBytes: chunkBufferPart.length,
    outputBytes: 0,
    sha256: "",
    inputBase64: chunkBufferPart.toString("base64"),
    resultBase64: "",
    lastError: "",
    lastStartedAt: "",
    completedAt: ""
  }));
  const jobId = randomUUID();
  state.jobs[jobId] = {
    id: jobId,
    fileName,
    safeBaseName,
    mimeType,
    status: "queued",
    createdAt: nowIso(),
    completedAt: "",
    chunkSizeBytes,
    totalBytes: buffer.length,
    outputArchiveName: "",
    outputArchiveBase64: "",
    chunks
  };
  appendEvent(state, "job-created", `Tarefa ${fileName} entrou na fila com ${chunks.length} partes.`, {
    jobId
  });
  await saveState(state);
  sendJson(response, 200, {
    ok: true,
    jobId,
    chunkCount: chunks.length
  });
}

async function handleAdminDownload(request, response, jobId) {
  const state = await loadState();
  const job = state.jobs[jobId];
  if (!job) {
    notFound(response);
    return;
  }
  if (job.status !== "completed" || !job.outputArchiveBase64) {
    sendJson(response, 409, { error: "Tarefa ainda nao concluida." });
    return;
  }
  const archive = Buffer.from(job.outputArchiveBase64, "base64");
  response.writeHead(200, {
    "Content-Type": "application/zip",
    "Content-Disposition": `attachment; filename="${job.outputArchiveName}"`,
    "Content-Length": archive.length
  });
  response.end(archive);
}

async function handleWorkerRegister(request, response) {
  const body = await readJsonBody(request);
  const state = await loadState();
  const providedId = String(body.deviceId || "").trim();
  const deviceId = providedId || randomUUID();
  const deviceName = String(body.deviceName || "Navegador").trim() || "Navegador";
  const existing = state.devices[deviceId] || {};
  state.devices[deviceId] = {
    id: deviceId,
    name: deviceName,
    userAgent: request.headers["user-agent"] || "",
    configuredLoadPercent: state.config.workerMaxLoadPercent,
    estimatedLoadPercent: Number(body.estimatedLoadPercent || existing.estimatedLoadPercent || 0),
    firstSeenAt: existing.firstSeenAt || nowIso(),
    lastSeenAt: nowIso(),
    lastAssignedAt: existing.lastAssignedAt || "",
    currentJobId: existing.currentJobId || "",
    currentChunkId: existing.currentChunkId || "",
    completedChunks: existing.completedChunks || 0,
    failedChunks: existing.failedChunks || 0,
    averageProcessingMs: existing.averageProcessingMs || 0
  };
  appendEvent(state, "device-registered", `Dispositivo ${deviceName} conectado ao cluster.`, {
    deviceId
  });
  await saveState(state);
  sendJson(response, 200, {
    ok: true,
    deviceId,
    config: state.config
  });
}

async function handleWorkerHeartbeat(request, response) {
  const body = await readJsonBody(request);
  const deviceId = String(body.deviceId || "");
  const state = await loadState();
  const device = state.devices[deviceId];
  if (!device) {
    notFound(response);
    return;
  }
  device.lastSeenAt = nowIso();
  device.estimatedLoadPercent = Number(body.estimatedLoadPercent || device.estimatedLoadPercent || 0);
  await saveState(state);
  sendJson(response, 200, { ok: true });
}

async function handleWorkerPoll(request, response) {
  const body = await readJsonBody(request);
  const deviceId = String(body.deviceId || "");
  const state = await loadState();
  reclaimStaleChunks(state);
  const device = state.devices[deviceId];
  if (!device) {
    notFound(response);
    return;
  }
  device.lastSeenAt = nowIso();
  const assignment = nextQueuedChunk(state, deviceId);
  await saveState(state);
  if (!assignment) {
    sendJson(response, 200, { assignment: null, config: state.config });
    return;
  }
  sendJson(response, 200, {
    config: state.config,
    assignment: {
      jobId: assignment.job.id,
      fileName: assignment.job.fileName,
      chunkId: assignment.chunk.id,
      index: assignment.chunk.index,
      inputBase64: assignment.chunk.inputBase64,
      inputBytes: assignment.chunk.inputBytes,
      targetLoadPercent: state.config.workerMaxLoadPercent,
      leaseExpiresAt: assignment.chunk.leaseExpiresAt
    }
  });
}

async function handleWorkerResult(request, response) {
  const body = await readJsonBody(request);
  const state = await loadState();
  const deviceId = String(body.deviceId || "");
  const jobId = String(body.jobId || "");
  const chunkId = String(body.chunkId || "");
  const job = state.jobs[jobId];
  const device = state.devices[deviceId];
  if (!job || !device) {
    notFound(response);
    return;
  }
  const chunk = job.chunks.find((item) => item.id === chunkId);
  if (!chunk) {
    notFound(response);
    return;
  }
  if (body.status === "completed") {
    chunk.status = "completed";
    chunk.resultBase64 = String(body.resultBase64 || "");
    chunk.outputBytes = Number(body.outputBytes || 0);
    chunk.sha256 = String(body.sha256 || "");
    chunk.completedAt = nowIso();
    chunk.leaseExpiresAt = 0;
    device.completedChunks += 1;
    appendEvent(state, "chunk-completed", `Chunk ${chunk.id} da tarefa ${job.fileName} foi concluido.`, {
      jobId,
      chunkId,
      deviceId
    });
  } else {
    chunk.status = "queued";
    chunk.assignedDeviceId = "";
    chunk.leaseExpiresAt = 0;
    chunk.lastError = String(body.errorMessage || "Falha no worker.");
    device.failedChunks += 1;
    appendEvent(state, "chunk-failed", `Chunk ${chunk.id} voltou para a fila apos falha no worker.`, {
      jobId,
      chunkId,
      deviceId
    });
  }
  const processingMs = Number(body.processingMs || 0);
  if (processingMs > 0) {
    if (device.averageProcessingMs <= 0) {
      device.averageProcessingMs = processingMs;
    } else {
      device.averageProcessingMs = Math.round((device.averageProcessingMs + processingMs) / 2);
    }
  }
  device.estimatedLoadPercent = Number(body.estimatedLoadPercent || 0);
  device.lastSeenAt = nowIso();
  device.currentJobId = "";
  device.currentChunkId = "";
  reclaimStaleChunks(state);
  if (job.status === "completed" && !job.outputArchiveBase64) {
    finalizeJobArchive(job);
    appendEvent(state, "job-completed", `Tarefa ${job.fileName} concluida e pronta para download.`, {
      jobId
    });
  }
  await saveState(state);
  sendJson(response, 200, { ok: true, jobStatus: job.status });
}

async function handlePublicConfig(response) {
  const state = await loadState();
  sendJson(response, 200, {
    publicBaseUrl: state.config.publicBaseUrl,
    workerMaxLoadPercent: state.config.workerMaxLoadPercent,
    workerPollMs: state.config.workerPollMs,
    updatedAt: state.config.updatedAt
  });
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    if (request.method === "GET" && !url.pathname.startsWith("/api/")) {
      await serveStatic(request, response);
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/admin/login") {
      await handleAdminLogin(request, response);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/admin/logout") {
      await handleAdminLogout(response);
      return;
    }
    if (request.method === "GET" && url.pathname === "/api/public/config") {
      await handlePublicConfig(response);
      return;
    }

    if (url.pathname.startsWith("/api/admin/") && !isAuthenticated(request)) {
      unauthorized(response);
      return;
    }

    if (request.method === "GET" && url.pathname === "/api/admin/state") {
      await handleAdminState(response);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/admin/config") {
      await handleAdminConfig(request, response);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/admin/tasks") {
      await handleAdminCreateTask(request, response);
      return;
    }
    if (request.method === "GET" && url.pathname.startsWith("/api/admin/tasks/") && url.pathname.endsWith("/download")) {
      const jobId = url.pathname.split("/")[4];
      await handleAdminDownload(request, response, jobId);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/worker/register") {
      await handleWorkerRegister(request, response);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/worker/heartbeat") {
      await handleWorkerHeartbeat(request, response);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/worker/poll") {
      await handleWorkerPoll(request, response);
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/worker/result") {
      await handleWorkerResult(request, response);
      return;
    }

    notFound(response);
  } catch (error) {
    sendJson(response, 500, { error: error.message || "Erro interno." });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Cluster bridge ativo em http://${HOST}:${PORT}`);
});
