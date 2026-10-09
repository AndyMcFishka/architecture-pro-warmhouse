import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Calls the internal registry and legacy adapter, never the public temperature API. */
public final class MonolithClient {

  private final HttpClient http;
  private final String address;
  private final Duration timeout;

  public MonolithClient(String address, Duration timeout) {
    this.address = address;
    this.timeout = timeout;
    this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  private JsonNode get(String path) throws Exception {
    var response = http.send(
      HttpRequest.newBuilder(URI.create(address + path))
        .timeout(timeout)
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    if (response.statusCode() != 200) throw new ApiError(
      response.statusCode() == 404 ? 404 : 503
    );
    return JsonCodec.MAPPER.readTree(response.body());
  }

  public DeviceSpec getDevice(long id) throws Exception {
    return DeviceSpec.fromJson(get("/internal/v1/devices/" + id));
  }

  public DeviceSpec findByLocation(String location) throws Exception {
    var devices = get(
      "/internal/v1/devices?type=temperature&location=" +
      URLEncoder.encode(location, StandardCharsets.UTF_8)
    );
    ApiError.require(!devices.isEmpty(), 404);
    ApiError.require(devices.size() == 1, 409);
    return DeviceSpec.fromJson(devices.get(0));
  }

  public List<DeviceSpec> listLegacySensors() throws Exception {
    var devices = new ArrayList<DeviceSpec>();
    for (var json : get(
      "/internal/v1/devices?kind=SENSOR&telemetry_mode=PULL&connection_mode=LEGACY"
    )) devices.add(DeviceSpec.fromJson(json));
    return devices;
  }

  public Measurement pollLegacySensor(long id) throws Exception {
    var json = (ObjectNode) get(
      "/internal/v1/legacy/devices/" + id + "/measurement"
    );
    json.put("measurement_id", UUID.randomUUID().toString());
    return Measurement.fromJson(json);
  }
}
