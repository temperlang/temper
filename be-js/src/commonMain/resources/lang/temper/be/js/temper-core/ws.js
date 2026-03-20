import { empty } from "./core.js";

// We dynamically import 'ws' so this module works in environments where
// it is not installed (the functions will throw at call time instead of
// at import time).
let _WebSocketServer;
let _WebSocket;
const _wsReady = (async () => {
  try {
    const ws = await import("ws");
    _WebSocketServer = ws.WebSocketServer;
    _WebSocket = ws.default || ws.WebSocket;
  } catch (_) {
    // ws not available — functions will throw when called
  }
})();

function _requireWs() {
  if (!_WebSocket) {
    throw new Error("WebSocket support requires the 'ws' npm package. Run: npm install ws");
  }
}

function _setupMessageQueue(ws) {
  if (!ws._mq) {
    ws._mq = [];
    ws._wr = [];
    ws._closed = false;
    ws.on("message", (data) => {
      const msg = data.toString();
      const waiting = ws._wr.shift();
      if (waiting) { waiting(msg); }
      else { ws._mq.push(msg); }
    });
    ws.on("close", () => {
      ws._closed = true;
      while (ws._wr.length) {
        ws._wr.shift()(null);
      }
    });
    ws.on("error", () => {
      ws._closed = true;
      while (ws._wr.length) {
        ws._wr.shift()(null);
      }
    });
  }
}

/**
 * @param {number} port
 * @returns {Promise<object>}
 */
export async function wsListen(port) {
  await _wsReady;
  _requireWs();
  return new Promise((resolve, reject) => {
    const server = new _WebSocketServer({ port });
    server._pending = [];
    server._waiting = [];
    server.on("connection", (ws) => {
      _setupMessageQueue(ws);
      const waiting = server._waiting.shift();
      if (waiting) { waiting(ws); }
      else { server._pending.push(ws); }
    });
    server.on("listening", () => resolve(server));
    server.on("error", reject);
  });
}

/**
 * @param {object} server
 * @returns {Promise<object>}
 */
export function wsAccept(server) {
  return new Promise((resolve) => {
    const pending = server._pending.shift();
    if (pending) { resolve(pending); }
    else { server._waiting.push(resolve); }
  });
}

/**
 * @param {string} url
 * @returns {Promise<object>}
 */
export async function wsConnect(url) {
  await _wsReady;
  _requireWs();
  return new Promise((resolve, reject) => {
    const ws = new _WebSocket(url);
    _setupMessageQueue(ws);
    ws.on("open", () => resolve(ws));
    ws.on("error", reject);
  });
}

/**
 * @param {object} conn
 * @param {string} msg
 * @returns {Promise<Empty>}
 */
export function wsSend(conn, msg) {
  return new Promise((resolve, reject) => {
    conn.send(msg, (err) => {
      if (err) reject(err);
      else resolve(empty());
    });
  });
}

/**
 * @param {object} conn
 * @returns {Promise<string | null>}
 */
export function wsRecv(conn) {
  _setupMessageQueue(conn);
  return new Promise((resolve) => {
    if (conn._closed && conn._mq.length === 0) {
      resolve(null);
      return;
    }
    const queued = conn._mq.shift();
    if (queued !== undefined) { resolve(queued); }
    else { conn._wr.push(resolve); }
  });
}

/**
 * @param {object} conn
 * @returns {Promise<Empty>}
 */
export function wsClose(conn) {
  return new Promise((resolve) => {
    if (conn.readyState !== undefined && conn.readyState > 1) {
      resolve(empty());
      return;
    }
    conn.on("close", () => resolve(empty()));
    conn.close();
  });
}
