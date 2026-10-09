import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/** Persisted command; desired state is distinct from observed equipment state. */
data class Command(
    val id: UUID,
    @get:JsonProperty("idempotency_key") val idempotencyKey: UUID,
    @get:JsonProperty("device_id") val deviceId: Int,
    val enabled: Boolean,
    val status: String,
    @get:JsonProperty("created_at") val createdAt: String,
    @get:JsonProperty("updated_at") val updatedAt: String,
)

class Failure(val status: Int) : RuntimeException()

fun ensure(ok: Boolean, status: Int = 400) {
    if (!ok) throw Failure(status)
}
