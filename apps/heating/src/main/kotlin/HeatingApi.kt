import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import java.util.UUID

class HeatingApi(private val service: HeatingService, private val store: CommandStore) {
    private val json = ObjectMapper()

    fun handle(x: HttpExchange) {
        var status = 200
        val result: Any =
            try {
                val path = x.requestURI.path
                val device =
                    Regex("/api/v1/heating/devices/([0-9]+)/(commands|state)").matchEntire(path)
                val command = Regex("/api/v1/heating/commands/([^/]+)").matchEntire(path)
                when {
                    path == "/health" && x.requestMethod == "GET" -> {
                        store.checkHealth()
                        mapOf("status" to "ok")
                    }
                    device != null -> {
                        val id = device.groupValues[1].toInt()
                        ensure(id > 0)
                        if (device.groupValues[2] == "state" && x.requestMethod == "GET") {
                            service.state(id)
                        } else {
                            ensure(
                                device.groupValues[2] == "commands" && x.requestMethod == "POST",
                                405,
                            )
                            val key =
                                UUID.fromString(x.requestHeaders.getFirst("Idempotency-Key") ?: "")
                            val bytes = x.requestBody.readNBytes(65537)
                            ensure(bytes.size <= 65536, 413)
                            val n = json.readTree(bytes)
                            ensure(
                                n != null &&
                                    n.isObject &&
                                    n.size() == 1 &&
                                    n.path("enabled").isBoolean
                            )
                            service.command(id, key, n.get("enabled").asBoolean())
                        }
                    }
                    command != null && x.requestMethod == "GET" ->
                        service.getCommand(UUID.fromString(command.groupValues[1]))
                    else -> throw Failure(404)
                }
            } catch (e: Exception) {
                status =
                    when (e) {
                        is Failure -> e.status
                        is IllegalArgumentException,
                        is com.fasterxml.jackson.core.JsonProcessingException -> 400
                        else -> 503
                    }
                mapOf("error" to "Request failed")
            }
        val bytes = json.writeValueAsBytes(result)
        x.responseHeaders.set("Content-Type", "application/json")
        x.responseHeaders.set("Cache-Control", "no-store")
        x.sendResponseHeaders(status, bytes.size.toLong())
        x.responseBody.use { it.write(bytes) }
        x.close()
    }
}
