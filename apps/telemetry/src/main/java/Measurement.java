import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** An immutable reading, shared by push ingestion and legacy polling. */
public record Measurement(
  long deviceId,
  UUID id,
  String metric,
  double value,
  String unit,
  Instant measuredAt,
  Instant receivedAt
) {
  public static Measurement fromJson(JsonNode json) {
    ApiError.require(
      json != null &&
      json.isObject() &&
      json.size() == 6 &&
      json.path("device_id").isIntegralNumber() &&
      json.path("device_id").asLong() > 0 &&
      json.path("value").isNumber() &&
      Double.isFinite(json.path("value").asDouble()),
      400
    );
    for (String key : new String[] {
      "measurement_id",
      "metric",
      "unit",
      "measured_at",
    }) ApiError.require(
      json.path(key).isTextual() && !json.get(key).asText().isBlank(),
      400
    );
    return new Measurement(
      json.get("device_id").asLong(),
      UUID.fromString(json.get("measurement_id").asText()),
      json.get("metric").asText(),
      json.get("value").asDouble(),
      json.get("unit").asText(),
      Instant.parse(json.get("measured_at").asText()).truncatedTo(
        ChronoUnit.MICROS
      ),
      null
    );
  }
  public boolean samePayload(Measurement other) {
    return (
      deviceId == other.deviceId &&
      id.equals(other.id) &&
      metric.equals(other.metric) &&
      value == other.value &&
      unit.equals(other.unit) &&
      measuredAt.equals(other.measuredAt)
    );
  }
  public ObjectNode toJson() {
    var json = JsonCodec.MAPPER.createObjectNode()
      .put("device_id", deviceId)
      .put("measurement_id", id.toString())
      .put("metric", metric)
      .put("value", value)
      .put("unit", unit)
      .put("measured_at", measuredAt.toString());
    if (receivedAt != null) json.put("received_at", receivedAt.toString());
    return json;
  }
}
