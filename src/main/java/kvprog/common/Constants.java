package kvprog.common;

import io.grpc.Context;

public class Constants {

  private Constants() {} // do not instantiate.

  public static final Context.Key<Integer> TRACE_ID_CTX_KEY = Context.key("trace_id");
}
