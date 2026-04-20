# WebSocket Support

WebSocket support for real-time bidirectional communication.

*WsServer* is an opaque handle representing a listening WebSocket server.

    @connected("WsServer")
    export interface WsServer {}

*WsConnection* is an opaque handle representing a single WebSocket connection.

    @connected("WsConnection")
    export interface WsConnection {}

## Server Functions

*wsListen* starts a WebSocket server on the given port and resolves when
it is ready to accept connections.

    @connected("wsListen")
    export let wsListen(port: Int): Promise<WsServer> {
      panic()
    }

*wsAccept* waits for and accepts the next incoming connection on a server.

    @connected("wsAccept")
    export let wsAccept(server: WsServer): Promise<WsConnection> {
      panic()
    }

## Client Functions

*wsConnect* opens a WebSocket connection to the given URL
(e.g. `"ws://localhost:8080"`).

    @connected("wsConnect")
    export let wsConnect(url: String): Promise<WsConnection> {
      panic()
    }

## Shared Functions

*wsSend* sends a text message over a connection.

    @connected("wsSend")
    export let wsSend(conn: WsConnection, msg: String): Promise<Empty> {
      panic()
    }

*wsRecv* waits for the next message from a connection.
Returns `null` if the connection is closed.

    @connected("wsRecv")
    export let wsRecv(conn: WsConnection): Promise<String?> {
      panic()
    }

*wsClose* closes a connection.

    @connected("wsClose")
    export let wsClose(conn: WsConnection): Promise<Empty> {
      panic()
    }
