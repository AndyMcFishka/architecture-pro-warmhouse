import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;

/** Composition root: construct collaborators and start HTTP and periodic polling. */
public class Main {

  public static void main(String[] args) throws Exception {
    var env = System.getenv();
    var registry = new MonolithClient(
      env.getOrDefault("MONOLITH_URL", "http://app:8080"),
      Duration.ofSeconds(
        Long.parseLong(env.getOrDefault("HTTP_TIMEOUT_SECONDS", "4"))
      )
    );
    var store = new MeasurementStore(env.get("DATABASE_URL"));
    var processor = new MeasurementProcessor(registry, store);
    var api = new TelemetryApi(
      processor,
      store,
      registry,
      Duration.ofSeconds(
        Long.parseLong(env.getOrDefault("STALE_AFTER_SECONDS", "60"))
      )
    );
    var poller = new LegacySensorPoller(registry, processor);
    var server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/", api::handle);
    server.setExecutor(Executors.newFixedThreadPool(8));
    server.start();
    Executors.newSingleThreadScheduledExecutor()
      .scheduleWithFixedDelay(
        poller::pollOnce,
        0,
        Long.parseLong(env.getOrDefault("POLL_INTERVAL_SECONDS", "5")),
        TimeUnit.SECONDS
      );
  }
}
