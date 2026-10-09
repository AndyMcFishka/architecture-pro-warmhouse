import java.util.UUID

/** Heating decisions and idempotency; no HTTP or SQL mechanics. */
class HeatingService(private val store: CommandStore, private val devices: ConnectivityClient) {
    private fun repeat(command: Command, device: Int, enabled: Boolean): Command {
        ensure(command.deviceId == device && command.enabled == enabled, 409)
        return command
    }

    fun state(device: Int) =
        devices.state(device).also { ensure(it.path("kind").asText() == "HEATING", 422) }

    fun getCommand(id: UUID) = store.find(id) ?: throw Failure(404)

    fun command(device: Int, key: UUID, enabled: Boolean): Command {
        store.findByKey(key)?.let {
            return repeat(it, device, enabled)
        }
        state(device)
        val command =
            store.create(device, key, enabled)
                ?: return repeat(store.findByKey(key)!!, device, enabled)
        val status =
            try {
                devices.send(command)
            } catch (error: Exception) {
                if (error is Failure && error.status in listOf(400, 404, 409, 422)) "FAILED"
                else "UNKNOWN"
            }
        return store.saveResult(command.id, status)
    }
}
