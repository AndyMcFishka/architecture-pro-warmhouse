public class ApiError extends RuntimeException {

  public final int status;

  public ApiError(int status) {
    this.status = status;
  }

  public static void require(boolean valid, int status) {
    if (!valid) throw new ApiError(status);
  }
}
