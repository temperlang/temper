use super::*;
use std::sync::{Arc, Mutex};
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

#[cfg(feature = "ws")]
enum WsStream {
    Plain(WebSocket<TcpStream>),
    MaybeTls(WebSocket<MaybeTlsStream<TcpStream>>),
}

#[cfg(feature = "ws")]
impl WsStream {
    fn send(&mut self, msg: Message) -> Result<(), tungstenite::Error> {
        match self {
            WsStream::Plain(ws) => ws.send(msg),
            WsStream::MaybeTls(ws) => ws.send(msg),
        }
    }
    fn read(&mut self) -> Result<Message, tungstenite::Error> {
        match self {
            WsStream::Plain(ws) => ws.read(),
            WsStream::MaybeTls(ws) => ws.read(),
        }
    }
    fn close(&mut self, frame: Option<tungstenite::protocol::CloseFrame>) -> Result<(), tungstenite::Error> {
        match self {
            WsStream::Plain(ws) => ws.close(frame),
            WsStream::MaybeTls(ws) => ws.close(frame),
        }
    }
}

#[cfg(feature = "ws")]
struct WsServerInner {
    listener: Mutex<TcpListener>,
}

#[cfg(feature = "ws")]
#[derive(Clone)]
struct SimpleWsServer(Arc<WsServerInner>);

#[cfg(feature = "ws")]
impl WsServerTrait for SimpleWsServer {
    fn clone_boxed(&self) -> WsServer {
        WsServer::new(self.clone())
    }
}

#[cfg(feature = "ws")]
temper_core::impl_any_value_trait!(SimpleWsServer, [WsServer]);

#[cfg(feature = "ws")]
struct WsConnectionInner {
    socket: Mutex<WsStream>,
}

#[cfg(feature = "ws")]
#[derive(Clone)]
struct SimpleWsConnection(Arc<WsConnectionInner>);

#[cfg(feature = "ws")]
impl WsConnectionTrait for SimpleWsConnection {
    fn clone_boxed(&self) -> WsConnection {
        WsConnection::new(self.clone())
    }
}

#[cfg(feature = "ws")]
temper_core::impl_any_value_trait!(SimpleWsConnection, [WsConnection]);

#[cfg(feature = "ws")]
pub fn std_ws_listen(port: i32) -> Promise<WsServer> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            let addr = format!("0.0.0.0:{}", port);
            match TcpListener::bind(&addr) {
                Ok(listener) => {
                    pb.complete(WsServer::new(SimpleWsServer(Arc::new(WsServerInner {
                        listener: Mutex::new(listener),
                    }))));
                }
                Err(_) => {
                    pb.break_promise();
                }
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
            Ok((stream, _addr)) => {
                drop(listener);
                match ws_upgrade(stream) {
                    Ok(ws) => {
                        pb.complete(WsConnection::new(SimpleWsConnection(Arc::new(
                            WsConnectionInner {
                                socket: Mutex::new(WsStream::Plain(ws)),
                            },
                        ))));
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
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            match ws_connect_url(url.as_str()) {
                Ok((ws, _response)) => {
                    pb.complete(WsConnection::new(SimpleWsConnection(Arc::new(
                        WsConnectionInner {
                            socket: Mutex::new(WsStream::MaybeTls(ws)),
                        },
                    ))));
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
    std::thread::spawn(move || {
        let mut socket = conn.0.socket.lock().unwrap();
        match socket.send(Message::Text(msg.to_string().into())) {
            Ok(_) => pb.complete(()),
            Err(_) => pb.break_promise(),
        }
    });
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_recv(conn: &dyn WsConnectionTrait) -> Promise<Option<Arc<String>>> {
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("WsConnection downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Spawn a dedicated thread instead of using run_async to avoid
    // blocking the single-threaded task runner (which would prevent
    // other async blocks like readLine from processing).
    std::thread::spawn(move || {
        let mut socket = conn.0.socket.lock().unwrap();
        match socket.read() {
            Ok(Message::Text(text)) => {
                pb.complete(Some(Arc::new(text.to_string())));
            }
            Ok(Message::Close(frame)) => {
                eprintln!("[ws_recv] close frame: {:?}", frame);
                pb.complete(None);
            }
            Ok(other) => {
                eprintln!("[ws_recv] unexpected message type: {:?}", other);
                pb.complete(None);
            }
            Err(e) => {
                eprintln!("[ws_recv] error: {:?}", e);
                pb.complete(None);
            }
        }
    });
    promise
}

#[cfg(feature = "ws")]
pub fn std_ws_close(conn: &dyn WsConnectionTrait) -> Promise<()> {
    let conn: SimpleWsConnection = temper_core::cast(conn.as_any_value()).expect("WsConnection downcast");
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        let conn = conn.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            let mut socket = conn.0.socket.lock().unwrap();
            let _ = socket.close(None);
            loop {
                match socket.read() {
                    Ok(Message::Close(_)) | Err(_) => break,
                    _ => continue,
                }
            }
            pb.complete(());
            None
        }))
    }));
    promise
}
