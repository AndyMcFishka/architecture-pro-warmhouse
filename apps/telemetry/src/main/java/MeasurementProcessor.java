import java.time.Instant;

/** Applies the same measurement checks for both ingestion paths. */
public final class MeasurementProcessor {

  private final MonolithClient registry;
  private final MeasurementStore store;

  public MeasurementProcessor(MonolithClient registry, MeasurementStore store) {
    this.registry = registry;
    this.store = store;
  }

  public SaveResult accept(Measurement measurement, String sourceMode)
    throws Exception {
    var device = registry.getDevice(measurement.deviceId());
    ApiError.require(
      device.kind().equals("SENSOR") &&
      device.telemetryMode().equals(sourceMode) &&
      device.metric().equals(measurement.metric()) &&
      device.unit().equals(measurement.unit()),
      422
    );
    ApiError.require(
      Double.isFinite(measurement.value()) &&
      !measurement.measuredAt().isAfter(Instant.now().plusSeconds(30)),
      400
    );
    return store.saveUnique(measurement);
  }
}
