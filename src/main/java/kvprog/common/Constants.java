package kvprog.common;

import io.grpc.Context;

import java.time.Duration;

public class Constants {

  public static final Context.Key<Integer> TRACE_ID_CTX_KEY = Context.key("trace_id");

  private Constants() {
  } // do not instantiate.

  public static double durationToSec(Duration duration) {
    return duration.toNanos() / 1_000_000_000.0;
  }

  public static Duration secToDuration(double seconds) {
    return Duration.ofNanos(secToNanos(seconds * 1_000_000_000L));
  }

  public static long secToNanos(double seconds) {
    return Math.round(seconds * 1_000_000_000L);
  }
}
