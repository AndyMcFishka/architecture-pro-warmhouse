import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ConnectivityClient(private val address: String) {
    private val json = ObjectMapper()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    private fun call(path: String, body: Any? = null): JsonNode {
        val request =
            HttpRequest.newBuilder(URI.create(address + path)).timeout(Duration.ofSeconds(5))
        if (body != null)
            request
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        val response = http.send(request.build(), HttpResponse.BodyHandlers.ofString())
        ensure(response.statusCode() == 200, response.statusCode())
        return json.readTree(response.body())
    }

    fun state(deviceId: Int): JsonNode = call("/internal/v1/devices/$deviceId/state")

    fun send(command: Command): String =
        call(
                "/internal/v1/commands",
                mapOf(
                    "command_service" to "heating",
                    "command_id" to command.id.toString(),
                    "device_id" to command.deviceId,
                    "operation" to "SET_HEATING",
                    "parameters" to mapOf("enabled" to command.enabled),
                ),
            )
            .path("status")
            .asText()
}
