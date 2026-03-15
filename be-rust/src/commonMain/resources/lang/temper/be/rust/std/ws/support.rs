use super::*;
use std::sync::{Arc, Mutex, mpsc};
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

#[cfg(feature = "ws")]
use std::net::{TcpListener, TcpStream};
#[cfg(feature = "ws")]
use tungstenite::{accept as ws_upgrade, connect as ws_connect_url, stream::MaybeTlsStream, Message, WebSocket};

// --- Server ---

#[cfg(feature = "ws")]
struct WsServerInner {
    listener: Mutex<TcpListener>,
}

#[cfg(feature = "ws")]
#[derive(Clone)]
struct SimpleWsServer(Arc<WsServerInner>);

#[cfg(feature = "ws")]
impl WsServerTrait for SimpleWsServer {
    fn clone_boxed(&self) -> WsServer { WsServer::new(self.clone()) }
}

#[cfg(feature = "ws")]
temper_core::impl_any_value_trait!(SimpleWsServer, [WsServer]);

// --- Connection ---
// Each connection has a dedicated I/O thread that owns the WebSocket.
// Send and recv go through channels, avoiding Mutex sharing issues.

#[cfg(feature = "ws")]
struct WsConnectionInner {
    send_tx: mpsc::Sender<String>,
    recv_rx: Mutex<mpsc::Receiver<Option<String>>>,
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

/// Spawn an I/O thread that owns the WebSocket and bridges to channels.
#[cfg(feature = "ws")]
fn spawn_ws_io_thread<S>(mut ws: WebSocket<S>) -> SimpleWsConnection
where
    S: std::io::Read + std::io::Write + Send + 'static,
{
    let (send_tx, send_rx) = mpsc::channel::<String>();
    let (recv_tx, recv_rx) = mpsc::sync_channel::<Option<String>>(16);

    std::thread::spawn(move || {
        loop {
            // Try to send any queued outgoing messages.
            while let Ok(msg) = send_rx.try_recv() {
                if ws.send(Message::Text(msg.into())).is_err() {
                    let _ = recv_tx.send(None);
                    return;
                }
            }

            // Try to read one incoming message (may timeout quickly).
            match ws.read() {
                Ok(Message::Text(text)) => {
                    if recv_tx.send(Some(text.to_string())).is_err() {
                        return; // recv side dropped
                    }
                }
                Ok(Message::Close(_)) => {
                    let _ = recv_tx.send(None);
                    return;
                }
                Ok(Message::Ping(data)) => {
                    let _ = ws.send(Message::Pong(data));
                }
                Ok(_) => {} // skip binary, pong, etc
                Err(tungstenite::Error::Io(ref e))
                    if e.kind() == std::io::ErrorKind::WouldBlock
                        || e.kind() == std::io::ErrorKind::TimedOut =>
                {
                    // No data yet — brief yield then loop
                    std::thread::sleep(std::time::Duration::from_millis(5));
                }
                Err(_) => {
                    let _ = recv_tx.send(None);
                    return;
                }
            }
        }
    });

    SimpleWsConnection(Arc::new(WsConnectionInner {
        send_tx,
        recv_rx: Mutex::new(recv_rx),
    }))
}

/// Set a short read timeout on a TcpStream before passing it to tungstenite.
/// This makes the I/O thread's read() return quickly, allowing send interleaving.
#[cfg(feature = "ws")]
fn prepare_stream(stream: &TcpStream) {
    let _ = stream.set_read_timeout(Some(std::time::Duration::from_millis(20)));
}

// --- Functions ---

#[cfg(feature = "ws")]
pub fn std_ws_listen(port: i32) -> Promise<WsServer> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_: SafeGenerator<()>| {
            match TcpListener::bind(format!("0.0.0.0:{}", port)) {
                Ok(listener) => {
                    pb.complete(WsServer::new(SimpleWsServer(Arc::new(WsServerInner {
                        listener: Mutex::new(listener),
                    }))));
                }
                Err(_) => pb.break_promise(),
            }
            None
        }))
    }));
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_accept(server: &dyn WsServerTrait) -> Promise<WsConnection> {
    let server: SimpleWsServer = temper_core::cast(server.as_any_value()).expect("WsServer downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    std::thread::spawn(move || {
        let listener = server.0.listener.lock().unwrap();
        match listener.accept() {
            Ok((stream, _)) => {
                drop(listener);
                prepare_stream(&stream);
                match ws_upgrade(stream) {
                    Ok(ws) => {
                        let conn = spawn_ws_io_thread(ws);
                        pb.complete(WsConnection::new(conn));
                    }
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
            match ws_connect_url(url.as_str()) {
                Ok((mut ws, _)) => {
                    // Set read timeout on client connections
                    match ws.get_ref() {
                        MaybeTlsStream::Plain(s) => prepare_stream(s),
                        _ => {}
                    }
                    let conn = spawn_ws_io_thread(ws);
                    pb.complete(WsConnection::new(conn));
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
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("WsConnection downcast");
    let msg = msg.to_arc_string();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Send is non-blocking — just push to the channel
    match conn.0.send_tx.send(msg.to_string()) {
        Ok(_) => pb.complete(()),
        Err(_) => pb.break_promise(),
    }
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_recv(conn: &dyn WsConnectionTrait) -> Promise<Option<Arc<String>>> {
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("WsConnection downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Recv blocks waiting for the next message from the I/O thread
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
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("WsConnection downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Drop the send channel which signals the I/O thread to stop
    drop(conn);
    pb.complete(());
    promise
}
