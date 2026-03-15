use super::*;
use std::io::{Read, Write, BufRead, BufReader};
use std::sync::{Arc, Mutex, mpsc};
use std::net::{TcpListener, TcpStream};
use temper_core::{AsAnyValue, Promise, PromiseBuilder, SafeGenerator};

#[cfg(not(feature = "ws"))]
pub fn std_ws_listen(_port: i32) -> Promise<WsServer> { panic!() }
#[cfg(not(feature = "ws"))]
pub fn std_ws_accept(_server: &dyn WsServerTrait) -> Promise<WsConnection> { panic!() }
#[cfg(not(feature = "ws"))]
pub fn std_ws_connect(_url: impl temper_core::ToArcString) -> Promise<WsConnection> { panic!() }
#[cfg(not(feature = "ws"))]
pub fn std_ws_send(_conn: &dyn WsConnectionTrait, _msg: impl temper_core::ToArcString) -> Promise<()> { panic!() }
#[cfg(not(feature = "ws"))]
pub fn std_ws_recv(_conn: &dyn WsConnectionTrait) -> Promise<Option<Arc<String>>> { panic!() }
#[cfg(not(feature = "ws"))]
pub fn std_ws_close(_conn: &dyn WsConnectionTrait) -> Promise<()> { panic!() }

// --- Minimal WebSocket implementation (RFC 6455 text frames only) ---

#[cfg(feature = "ws")]
fn ws_write_text_frame(w: &mut impl Write, text: &str, mask: bool) -> std::io::Result<()> {
    let payload = text.as_bytes();
    let len = payload.len();
    // Opcode 0x81 = final frame, text
    w.write_all(&[0x81])?;
    let mask_bit = if mask { 0x80 } else { 0x00 };
    if len < 126 {
        w.write_all(&[(len as u8) | mask_bit])?;
    } else if len < 65536 {
        w.write_all(&[126 | mask_bit])?;
        w.write_all(&(len as u16).to_be_bytes())?;
    } else {
        w.write_all(&[127 | mask_bit])?;
        w.write_all(&(len as u64).to_be_bytes())?;
    }
    if mask {
        let mask_key: [u8; 4] = [0x12, 0x34, 0x56, 0x78]; // fixed mask for simplicity
        w.write_all(&mask_key)?;
        let masked: Vec<u8> = payload.iter().enumerate().map(|(i, b)| b ^ mask_key[i % 4]).collect();
        w.write_all(&masked)?;
    } else {
        w.write_all(payload)?;
    }
    w.flush()
}

/// Read a WebSocket frame. Returns None on close, Some(text) on text frame.
#[cfg(feature = "ws")]
fn ws_read_text_frame(r: &mut impl Read) -> std::io::Result<Option<String>> {
    let mut header = [0u8; 2];
    if r.read_exact(&mut header).is_err() { return Ok(None); }
    let opcode = header[0] & 0x0F;
    let masked = (header[1] & 0x80) != 0;
    let mut len = (header[1] & 0x7F) as u64;
    if len == 126 {
        let mut buf = [0u8; 2];
        r.read_exact(&mut buf)?;
        len = u16::from_be_bytes(buf) as u64;
    } else if len == 127 {
        let mut buf = [0u8; 8];
        r.read_exact(&mut buf)?;
        len = u64::from_be_bytes(buf);
    }
    let mut mask_key = [0u8; 4];
    if masked {
        r.read_exact(&mut mask_key)?;
    }
    let mut payload = vec![0u8; len as usize];
    r.read_exact(&mut payload)?;
    if masked {
        for (i, b) in payload.iter_mut().enumerate() {
            *b ^= mask_key[i % 4];
        }
    }
    match opcode {
        0x01 => Ok(Some(String::from_utf8_lossy(&payload).to_string())), // text
        0x08 => Ok(None), // close
        0x09 => Ok(Some(String::new())), // ping — return empty, caller can ignore
        _ => Ok(Some(String::new())), // skip other opcodes
    }
}

/// Server-side WebSocket handshake (accept upgrade request).
/// Reads byte-by-byte to avoid consuming past the headers.
#[cfg(feature = "ws")]
fn ws_server_handshake(stream: &mut TcpStream) -> std::io::Result<()> {
    let mut headers = Vec::new();
    let mut ws_key = String::new();
    // Read headers byte-by-byte to not read past \r\n\r\n
    loop {
        let mut byte = [0u8; 1];
        stream.read_exact(&mut byte)?;
        headers.push(byte[0]);
        if headers.len() >= 4 && &headers[headers.len()-4..] == b"\r\n\r\n" {
            break;
        }
    }
    let header_str = String::from_utf8_lossy(&headers);
    for line in header_str.lines() {
        if line.to_lowercase().starts_with("sec-websocket-key:") {
            ws_key = line.split(':').nth(1).unwrap_or("").trim().to_string();
        }
    }
    let accept = ws_accept_key(&ws_key);
    eprintln!("[ws_handshake] key='{}' accept='{}'", ws_key, accept);
    let response = format!(
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {}\r\n\r\n",
        accept
    );
    stream.write_all(response.as_bytes())?;
    stream.flush()
}

/// Client-side WebSocket handshake (send upgrade request).
/// Reads byte-by-byte to avoid consuming past the headers.
#[cfg(feature = "ws")]
fn ws_client_handshake(stream: &mut TcpStream, host: &str, path: &str) -> std::io::Result<()> {
    let key = "dGhlIHNhbXBsZSBub25jZQ=="; // fixed nonce, fine for our use
    let request = format!(
        "GET {} HTTP/1.1\r\nHost: {}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {}\r\nSec-WebSocket-Version: 13\r\n\r\n",
        path, host, key
    );
    stream.write_all(request.as_bytes())?;
    stream.flush()?;
    // Read response headers byte-by-byte to not consume past \r\n\r\n
    let mut headers = Vec::new();
    loop {
        let mut byte = [0u8; 1];
        stream.read_exact(&mut byte)?;
        headers.push(byte[0]);
        if headers.len() >= 4 && &headers[headers.len()-4..] == b"\r\n\r\n" {
            break;
        }
    }
    Ok(())
}

#[cfg(feature = "ws")]
fn ws_accept_key(key: &str) -> String {
    let combined = format!("{}258EAFA5-E914-47DA-95CA-C5AB0DC85B11", key);
    let digest = sha1_smol::Sha1::from(combined).digest().bytes();
    base64_encode(&digest)
}

#[cfg(feature = "ws")]
fn base64_encode(data: &[u8]) -> String {
    const CHARS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut result = String::new();
    for chunk in data.chunks(3) {
        let b0 = chunk[0] as u32;
        let b1 = if chunk.len() > 1 { chunk[1] as u32 } else { 0 };
        let b2 = if chunk.len() > 2 { chunk[2] as u32 } else { 0 };
        let n = (b0 << 16) | (b1 << 8) | b2;
        result.push(CHARS[((n >> 18) & 63) as usize] as char);
        result.push(CHARS[((n >> 12) & 63) as usize] as char);
        if chunk.len() > 1 { result.push(CHARS[((n >> 6) & 63) as usize] as char); } else { result.push('='); }
        if chunk.len() > 2 { result.push(CHARS[(n & 63) as usize] as char); } else { result.push('='); }
    }
    result
}

// --- Server/Connection types ---

#[cfg(feature = "ws")]
struct WsServerInner { listener: Mutex<TcpListener> }

#[cfg(feature = "ws")]
#[derive(Clone)]
struct SimpleWsServer(Arc<WsServerInner>);

#[cfg(feature = "ws")]
impl WsServerTrait for SimpleWsServer {
    fn clone_boxed(&self) -> WsServer { WsServer::new(self.clone()) }
}

#[cfg(feature = "ws")]
temper_core::impl_any_value_trait!(SimpleWsServer, [WsServer]);

#[cfg(feature = "ws")]
struct WsConnectionInner {
    writer: Mutex<TcpStream>,   // write half
    recv_rx: Mutex<mpsc::Receiver<Option<String>>>,
    is_client: bool,
}

#[cfg(feature = "ws")]
#[derive(Clone)]
struct SimpleWsConnection(Arc<WsConnectionInner>);

#[cfg(feature = "ws")]
impl WsConnectionTrait for SimpleWsConnection {
    fn clone_boxed(&self) -> WsConnection { WsConnection::new(self.clone()) }
}

#[cfg(feature = "ws")]
temper_core::impl_any_value_trait!(SimpleWsConnection, [WsConnection]);

/// Create a connection from a TcpStream. Spawns a reader thread.
#[cfg(feature = "ws")]
fn make_connection(stream: TcpStream, is_client: bool) -> SimpleWsConnection {
    let writer = stream.try_clone().expect("clone stream for writer");
    let (recv_tx, recv_rx) = mpsc::sync_channel::<Option<String>>(32);

    // Reader thread owns the read half
    let is_client_thread = is_client;
    std::thread::spawn(move || {
        let mut reader = stream;
        eprintln!("[ws_reader] started (client={})", is_client_thread);
        loop {
            match ws_read_text_frame(&mut reader) {
                Ok(Some(text)) if text.is_empty() => continue,
                Ok(Some(text)) => {
                    eprintln!("[ws_reader] got {} bytes", text.len());
                    if recv_tx.send(Some(text)).is_err() { return; }
                }
                Ok(None) => {
                    eprintln!("[ws_reader] got close frame");
                    let _ = recv_tx.send(None);
                    return;
                }
                Err(e) => {
                    eprintln!("[ws_reader] error: {:?}", e);
                    let _ = recv_tx.send(None);
                    return;
                }
            }
        }
    });

    SimpleWsConnection(Arc::new(WsConnectionInner {
        writer: Mutex::new(writer),
        recv_rx: Mutex::new(recv_rx),
        is_client,
    }))
}

// --- Public API ---

#[cfg(feature = "ws")]
pub fn std_ws_listen(port: i32) -> Promise<WsServer> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_: SafeGenerator<()>| {
            match TcpListener::bind(format!("0.0.0.0:{}", port)) {
                Ok(listener) => pb.complete(WsServer::new(SimpleWsServer(Arc::new(
                    WsServerInner { listener: Mutex::new(listener) },
                )))),
                Err(_) => pb.break_promise(),
            }
            None
        }))
    }));
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_accept(server: &dyn WsServerTrait) -> Promise<WsConnection> {
    let server: SimpleWsServer = temper_core::cast(server.as_any_value()).expect("downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    std::thread::spawn(move || {
        let listener = server.0.listener.lock().unwrap();
        match listener.accept() {
            Ok((mut stream, _)) => {
                drop(listener);
                match ws_server_handshake(&mut stream) {
                    Ok(()) => pb.complete(WsConnection::new(make_connection(stream, false))),
                    Err(_) => pb.break_promise(),
                }
            }
            Err(_) => pb.break_promise(),
        }
    });
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_connect(url: impl temper_core::ToArcString) -> Promise<WsConnection> {
    let url = url.to_arc_string();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        let url = url.clone();
        SafeGenerator::from_fn(Arc::new(move |_: SafeGenerator<()>| {
            // Parse ws://host:port/path
            let url_str = url.as_str();
            let stripped = url_str.strip_prefix("ws://").unwrap_or(url_str);
            let (host_port, path) = stripped.split_once('/').unwrap_or((stripped, ""));
            let path = format!("/{}", path);
            match TcpStream::connect(host_port) {
                Ok(mut stream) => {
                    match ws_client_handshake(&mut stream, host_port, &path) {
                        Ok(()) => pb.complete(WsConnection::new(make_connection(stream, true))),
                        Err(_) => pb.break_promise(),
                    }
                }
                Err(_) => pb.break_promise(),
            }
            None
        }))
    }));
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_send(conn: &dyn WsConnectionTrait, msg: impl temper_core::ToArcString) -> Promise<()> {
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("downcast");
    let msg = msg.to_arc_string();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Write directly to the writer half (no contention with reader thread)
    let mut writer = conn.0.writer.lock().unwrap();
    let msg_len = msg.len();
    match ws_write_text_frame(&mut *writer, &msg, conn.0.is_client) {
        Ok(()) => {
            eprintln!("[ws_send] sent {} bytes OK", msg_len);
            pb.complete(());
        }
        Err(e) => {
            eprintln!("[ws_send] write error: {:?}", e);
            pb.break_promise();
        }
    }
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_recv(conn: &dyn WsConnectionTrait) -> Promise<Option<Arc<String>>> {
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    std::thread::spawn(move || {
        let rx = conn.0.recv_rx.lock().unwrap();
        match rx.recv() {
            Ok(Some(text)) => pb.complete(Some(Arc::new(text))),
            _ => pb.complete(None),
        }
    });
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_close(conn: &dyn WsConnectionTrait) -> Promise<()> {
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Send close frame
    if let Ok(mut writer) = conn.0.writer.lock() {
        let _ = writer.write_all(&[0x88, 0x00]); // close frame
        let _ = writer.flush();
    }
    pb.complete(());
    promise
}
