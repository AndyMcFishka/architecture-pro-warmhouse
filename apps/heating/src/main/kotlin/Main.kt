import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val store = CommandStore(System.getenv("DATABASE_URL"))
    val devices =
        ConnectivityClient(System.getenv("CONNECTIVITY_URL") ?: "http://connectivity:8080")
    val api = HeatingApi(HeatingService(store, devices), store)
    val server = HttpServer.create(InetSocketAddress(8080), 0)
    server.createContext("/", api::handle)
    server.executor = Executors.newFixedThreadPool(8)
    server.start()
}
