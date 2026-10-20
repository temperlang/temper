package lang.temper.common

import com.sun.net.httpserver.HttpServer
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.value.TInt
import lang.temper.value.Value
import java.net.InetSocketAddress

class TestWebServer(private val requestedPort: Int = 0) {
    private var server: HttpServer? = null

    /** Throws if not currently started. */
    fun makeBindings(): Map<TemperName, Value<*>> =
        mapOf(BuiltinName("testServerPort") to Value(port!!, TInt))

    val port get() = server?.address?.port

    fun start() {
        val server = when (server) {
            null -> HttpServer.create(InetSocketAddress("127.0.0.1", requestedPort), 0)
            else -> error("already started")
        }
        this.server = server
        server.createContext("/") { exchange ->
            val method = exchange.requestMethod
            // Don't even worry about escaping for now.
            val response = """{"method": "$method"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(HTTP_OK, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        // A path the server does not have, for checking that an HTTP error
        // status reaches Temper code as a response with that status.
        server.createContext("/missing") { exchange ->
            val response = "not here".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(HTTP_NOT_FOUND, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404
