import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** HTTP input/output only; measurement and registry work is delegated. */
public final class TelemetryApi {

  public static final ObjectMapper JSON = JsonCodec.MAPPER;
  private final MeasurementProcessor processor;
  private final MeasurementStore store;
  private final MonolithClient monolith;
  private final Duration staleAfter;

  public TelemetryApi(
    MeasurementProcessor processor,
    MeasurementStore store,
    MonolithClient monolith,
    Duration staleAfter
  ) {
    this.processor = processor;
    this.store = store;
    this.monolith = monolith;
    this.staleAfter = staleAfter;
  }

  private static void require(boolean valid, int status) {
    ApiError.require(valid, status);
  }

  private static long id(String text) {
    long id = Long.parseLong(text);
    require(id > 0, 400);
    return id;
  }

  public SaveResult postMeasurement(Measurement reading) throws Exception {
    return processor.accept(reading, "PUSH");
  }

  public Measurement getLatest(long deviceId, String metric) throws Exception {
    return store.latest(deviceId, metric);
  }

  public List<Measurement> getHistory(
    long id,
    String metric,
    Instant from,
    Instant to,
    int limit,
    int offset
  ) throws Exception {
    return store.history(id, metric, from, to, limit, offset);
  }

  public TemperatureResponse getTemperatureById(long id) throws Exception {
    return temperature(monolith.getDevice(id));
  }

  public TemperatureResponse getTemperatureByLocation(String location)
    throws Exception {
    return temperature(monolith.findByLocation(location));
  }

  private TemperatureResponse temperature(DeviceSpec device) throws Exception {
    require(device.type().equals("temperature"), 422);
    // sic! only for task 5 completion. Otherwise we will use polled value
    var reading = device.connectionMode().equals("LEGACY")
      ? monolith.pollLegacySensor(device.id())
      : store.latest(device.id(), device.metric());
    return TemperatureResponse.from(device, reading, staleAfter);
  }

  static Map<String, String> query(URI u) {
    var m = new HashMap<String, String>();
    if (u.getRawQuery() != null) for (String s : u.getRawQuery().split("&")) {
      var a = s.split("=", 2);
      m.put(
        URLDecoder.decode(a[0], StandardCharsets.UTF_8),
        a.length > 1 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : ""
      );
    }
    return m;
  }

  public void handle(HttpExchange x) throws java.io.IOException {
    int status = 200;
    JsonNode out;
    try {
      String path = x.getRequestURI().getPath();
      var params = query(x.getRequestURI());
      String method = x.getRequestMethod();
      if (path.equals("/health") && method.equals("GET")) {
        store.checkHealth();
        out = JSON.createObjectNode().put("status", "ok");
      } else if (
        path.equals("/api/v1/telemetry/measurements") && method.equals("POST")
      ) {
        byte[] bytes = x.getRequestBody().readNBytes(65537);
        require(bytes.length <= 65536, 413);
        var measurement = Measurement.fromJson(JSON.readTree(bytes));
        status = postMeasurement(measurement) == SaveResult.SAVED ? 201 : 200;
        out = store.find(measurement.deviceId(), measurement.id()).toJson();
      } else if (
        path.matches(
          "/api/v1/telemetry/devices/[0-9]+/(latest|measurements)"
        ) &&
        method.equals("GET")
      ) {
        long device = id(path.split("/")[5]);
        require(
          params.containsKey("metric") && !params.get("metric").isBlank(),
          400
        );
        var registered = monolith.getDevice(device);
        require(registered.metric().equals(params.get("metric")), 422);
        if (path.endsWith("/latest")) {
          out = getLatest(device, params.get("metric")).toJson();
        } else {
          Instant from = Instant.parse(params.getOrDefault("from", "")), to =
            Instant.parse(params.getOrDefault("to", ""));
          require(from.isBefore(to), 400);
          int limit = Integer.parseInt(
            params.getOrDefault("limit", "100")
          ), offset = Integer.parseInt(params.getOrDefault("offset", "0"));
          require(limit >= 1 && limit <= 1000 && offset >= 0, 400);
          var a = JSON.createArrayNode();
          for (var reading : getHistory(
            device,
            params.get("metric"),
            from,
            to,
            limit,
            offset
          )) a.add(reading.toJson());
          out = a;
        }
      } else if (
        (path.equals("/temperature") || path.matches("/temperature/[0-9]+")) &&
        method.equals("GET")
      ) {
        if (path.equals("/temperature")) {
          require(params.containsKey("location"), 400);
          out = getTemperatureByLocation(params.get("location")).toJson();
        } else out = getTemperatureById(id(path.substring(13))).toJson();
      } else throw new ApiError(404);
    } catch (ApiError e) {
      status = e.status;
      out = JSON.createObjectNode().put("error", "Request failed");
    } catch (
      IllegalArgumentException
      | java.time.format.DateTimeParseException
      | com.fasterxml.jackson.core.JsonProcessingException e
    ) {
      status = 400;
      out = JSON.createObjectNode().put("error", "Invalid request");
    } catch (Exception e) {
      status = 503;
      out = JSON.createObjectNode().put("error", "Dependency unavailable");
      System.err.println(e.getClass().getSimpleName());
    }
    byte[] bytes = JSON.writeValueAsBytes(out);
    x.getResponseHeaders().set("Content-Type", "application/json");
    x.getResponseHeaders().set("Cache-Control", "no-store");
    x.sendResponseHeaders(status, bytes.length);
    x.getResponseBody().write(bytes);
    x.close();
  }
}
