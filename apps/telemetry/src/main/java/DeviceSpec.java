import com.fasterxml.jackson.databind.JsonNode;

public record DeviceSpec(
  long id,
  String type,
  String kind,
  String metric,
  String unit,
  String connectionMode,
  String telemetryMode,
  String location
) {
  public static DeviceSpec fromJson(JsonNode json) {
    return new DeviceSpec(
      json.path("id").asLong(),
      json.path("type").asText(),
      json.path("kind").asText(),
      json.path("metric").asText(),
      json.path("unit").asText(),
      json.path("connection_mode").asText(),
      json.path("telemetry_mode").asText(),
      json.path("location").asText()
    );
  }
}
