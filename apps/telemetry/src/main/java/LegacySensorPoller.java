/** A failed sensor cannot stop the next sensor or the next polling cycle. */
public final class LegacySensorPoller {

  private final MonolithClient monolith;
  private final MeasurementProcessor processor;

  public LegacySensorPoller(
    MonolithClient monolith,
    MeasurementProcessor processor
  ) {
    this.monolith = monolith;
    this.processor = processor;
  }

  public void pollOnce() {
    try {
      for (var device : monolith.listLegacySensors()) {
        try {
          var reading = monolith.pollLegacySensor(device.id());
          processor.accept(reading, "PULL");
        } catch (Exception error) {
          System.err.println(
            "Legacy sensor " +
            device.id() +
            ": " +
            error.getClass().getSimpleName()
          );
        }
      }
    } catch (Exception error) {
      System.err.println("Registry unavailable for polling");
    }
  }
}
