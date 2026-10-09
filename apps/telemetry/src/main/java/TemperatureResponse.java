import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;

/** Compatibility response consumed by the original monolith temperature client. */
public record TemperatureResponse(
  double value,
  String unit,
  Instant timestamp,
  String location,
  String status,
  long sensorId
) {
  public static TemperatureResponse from(
    DeviceSpec device,
    Measurement reading,
    Duration staleAfter
  ) {
    return new TemperatureResponse(
      reading.value(),
      reading.unit(),
      reading.measuredAt(),
      device.location(),
      reading.measuredAt().isBefore(Instant.now().minus(staleAfter))
        ? "stale"
        : "active",
      device.id()
    );
  }
  public ObjectNode toJson() {
    return JsonCodec.MAPPER.createObjectNode()
      .put("value", value)
      .put("unit", unit)
      .put("timestamp", timestamp.toString())
      .put("location", location)
      .put("status", status)
      .put("sensor_id", Long.toString(sensorId))
      .put("sensor_type", "temperature")
      .put("description", "Temperature measurement");
  }
}
