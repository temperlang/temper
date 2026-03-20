use super::*;
use std::sync::{Arc, Mutex};
use std::collections::VecDeque;
use temper_core::{Promise, PromiseBuilder, SafeGenerator};

#[cfg(not(feature = "ws"))]
pub(crate) fn ws_listen(_port: i32) -> Promise<WsServer> { panic!() }
#[cfg(not(feature = "ws"))]
pub(crate) fn ws_accept(_server: &WsServer) -> Promise<WsConnection> { panic!() }
#[cfg(not(feature = "ws"))]
pub(crate) fn ws_connect(_url: impl temper_core::ToArcString) -> Promise<WsConnection> { panic!() }
#[cfg(not(feature = "ws"))]
pub(crate) fn ws_send(_conn: &WsConnection, _msg: impl temper_core::ToArcString) -> Promise<()> { panic!() }
#[cfg(not(feature = "ws"))]
pub(crate) fn ws_recv(_conn: &WsConnection) -> Promise<Option<Arc<String>>> { panic!() }
#[cfg(not(feature = "ws"))]
pub(crate) fn ws_close(_conn: &WsConnection) -> Promise<()> { panic!() }

#[cfg(feature = "ws")]
use std::net::{TcpListener, TcpStream};
#[cfg(feature = "ws")]
use tungstenite::{accept as ws_accept_tcp, connect as ws_connect_url, Message, WebSocket};

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
    socket: Mutex<WebSocket<TcpStream>>,
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
pub(crate) fn ws_listen(port: i32) -> Promise<WsServer> {
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
pub(crate) fn ws_accept(server: &WsServer) -> Promise<WsConnection> {
    let server_clone = server.clone_boxed();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        let server = server_clone.clone_boxed();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            // Downcast to get the listener
            let any = server.as_any_value();
            let inner = any.as_ref().as_any().downcast_ref::<SimpleWsServer>();
            match inner {
                Some(s) => {
                    let listener = s.0.listener.lock().unwrap();
                    match listener.accept() {
                        Ok((stream, _addr)) => {
                            drop(listener);
                            match ws_accept_tcp(stream) {
                                Ok(ws) => {
                                    pb.complete(WsConnection::new(SimpleWsConnection(Arc::new(
                                        WsConnectionInner {
                                            socket: Mutex::new(ws),
                                        },
                                    ))));
                                }
                                Err(_) => pb.break_promise(),
                            }
                        }
                        Err(_) => pb.break_promise(),
                    }
                }
                None => pb.break_promise(),
            }
            None
        }))
    }));
    promise
}

#[cfg(feature = "ws")]
pub(crate) fn ws_connect(url: impl temper_core::ToArcString) -> Promise<WsConnection> {
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
                            socket: Mutex::new(ws),
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
pub(crate) fn ws_send(conn: &WsConnection, msg: impl temper_core::ToArcString) -> Promise<()> {
    let conn_clone = conn.clone_boxed();
    let msg = msg.to_arc_string();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        let conn = conn_clone.clone_boxed();
        let msg = msg.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            let any = conn.as_any_value();
            let inner = any.as_ref().as_any().downcast_ref::<SimpleWsConnection>();
            match inner {
                Some(c) => {
                    let mut socket = c.0.socket.lock().unwrap();
                    match socket.send(Message::Text(msg.to_string())) {
                        Ok(_) => pb.complete(()),
                        Err(_) => pb.break_promise(),
                    }
                }
                None => pb.break_promise(),
            }
            None
        }))
    }));
    promise
}

#[cfg(feature = "ws")]
pub(crate) fn ws_recv(conn: &WsConnection) -> Promise<Option<Arc<String>>> {
    let conn_clone = conn.clone_boxed();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        let conn = conn_clone.clone_boxed();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            let any = conn.as_any_value();
            let inner = any.as_ref().as_any().downcast_ref::<SimpleWsConnection>();
            match inner {
                Some(c) => {
                    let mut socket = c.0.socket.lock().unwrap();
                    match socket.read() {
                        Ok(Message::Text(text)) => {
                            pb.complete(Some(Arc::new(text.to_string())));
                        }
                        Ok(Message::Close(_)) | Err(_) => {
                            pb.complete(None);
                        }
                        Ok(_) => {
                            // Binary or other message types — skip and return null
                            pb.complete(None);
                        }
                    }
                }
                None => pb.complete(None),
            }
            None
        }))
    }));
    promise
}

#[cfg(feature = "ws")]
pub(crate) fn ws_close(conn: &WsConnection) -> Promise<()> {
    let conn_clone = conn.clone_boxed();
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        let conn = conn_clone.clone_boxed();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            let any = conn.as_any_value();
            let inner = any.as_ref().as_any().downcast_ref::<SimpleWsConnection>();
            match inner {
                Some(c) => {
                    let mut socket = c.0.socket.lock().unwrap();
                    let _ = socket.close(None);
                    // Drain remaining messages until close confirmation
                    loop {
                        match socket.read() {
                            Ok(Message::Close(_)) | Err(_) => break,
                            _ => continue,
                        }
                    }
                }
                None => {}
            }
            pb.complete(());
            None
        }))
    }));
    promise
}
