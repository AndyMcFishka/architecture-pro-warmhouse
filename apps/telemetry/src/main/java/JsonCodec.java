import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonCodec {

  private JsonCodec() {}

  public static final ObjectMapper MAPPER = new ObjectMapper();
}
