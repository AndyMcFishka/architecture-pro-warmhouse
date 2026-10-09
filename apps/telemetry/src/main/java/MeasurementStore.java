import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Owns SQL and uniqueness. MeasurementProcessor owns validation against the registry. */
public final class MeasurementStore {

  private final String databaseUrl;

  public MeasurementStore(String databaseUrl) {
    this.databaseUrl = databaseUrl;
  }

  private Connection open() throws SQLException {
    return DriverManager.getConnection(databaseUrl, "telemetry", "telemetry");
  }

  public void checkHealth() throws SQLException {
    try (var connection = open(); var query = connection.createStatement()) {
      query.execute("SELECT 1");
    }
  }

  private Measurement read(ResultSet row) throws SQLException {
    return new Measurement(
      row.getLong("device_id"),
      row.getObject("measurement_id", UUID.class),
      row.getString("metric"),
      row.getDouble("value"),
      row.getString("unit"),
      row.getTimestamp("measured_at").toInstant(),
      row.getTimestamp("received_at").toInstant()
    );
  }

  public SaveResult saveUnique(Measurement value) throws SQLException {
    try (
      var connection = open();
      var query = connection.prepareStatement(
        "INSERT INTO measurements(device_id,measurement_id,metric,value,unit,measured_at) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING"
      )
    ) {
      query.setLong(1, value.deviceId());
      query.setObject(2, value.id());
      query.setString(3, value.metric());
      query.setDouble(4, value.value());
      query.setString(5, value.unit());
      query.setTimestamp(6, Timestamp.from(value.measuredAt()));
      if (query.executeUpdate() == 1) return SaveResult.SAVED;
    }
    ApiError.require(
      find(value.deviceId(), value.id()).samePayload(value),
      409
    );
    return SaveResult.DUPLICATE;
  }

  public Measurement find(long deviceId, UUID id) throws SQLException {
    try (
      var connection = open();
      var query = connection.prepareStatement(
        "SELECT * FROM measurements WHERE device_id=? AND measurement_id=?"
      )
    ) {
      query.setLong(1, deviceId);
      query.setObject(2, id);
      try (var row = query.executeQuery()) {
        ApiError.require(row.next(), 404);
        return read(row);
      }
    }
  }

  public Measurement latest(long deviceId, String metric) throws SQLException {
    try (
      var connection = open();
      var query = connection.prepareStatement(
        "SELECT * FROM measurements WHERE device_id=? AND metric=? ORDER BY measured_at DESC,measurement_id DESC LIMIT 1"
      )
    ) {
      query.setLong(1, deviceId);
      query.setString(2, metric);
      try (var row = query.executeQuery()) {
        ApiError.require(row.next(), 404);
        return read(row);
      }
    }
  }

  public List<Measurement> history(
    long deviceId,
    String metric,
    Instant from,
    Instant to,
    int limit,
    int offset
  ) throws SQLException {
    try (
      var connection = open();
      var query = connection.prepareStatement(
        "SELECT * FROM measurements WHERE device_id=? AND metric=? AND measured_at>=? AND measured_at<? ORDER BY measured_at,measurement_id LIMIT ? OFFSET ?"
      )
    ) {
      query.setLong(1, deviceId);
      query.setString(2, metric);
      query.setTimestamp(3, Timestamp.from(from));
      query.setTimestamp(4, Timestamp.from(to));
      query.setInt(5, limit);
      query.setInt(6, offset);
      var readings = new ArrayList<Measurement>();
      try (var row = query.executeQuery()) {
        while (row.next()) readings.add(read(row));
      }
      return readings;
    }
  }
}
