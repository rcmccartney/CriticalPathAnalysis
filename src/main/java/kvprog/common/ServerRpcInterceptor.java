package kvprog.common;

import com.google.common.base.Ticker;
import io.grpc.*;
import io.grpc.ServerCall.Listener;
import kvprog.common.InterceptorModule.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ServerRpcInterceptor implements ServerInterceptor {

  private final Map<Integer, InternalCriticalPath> criticalPaths;
  private final Metadata.Key<byte[]> criticalPathKey;
  private final AtomicInteger traceId;
  private final Metadata.Key<String> traceIdMetadataKey;
  private final Metadata.Key<String> elapsedTimeKey;
  private final Ticker ticker;

  @Inject
  ServerRpcInterceptor(
      @CriticalPaths Map<Integer, InternalCriticalPath> criticalPaths,
      @CriticalPathKey Metadata.Key<byte[]> criticalPathKey,
      @TraceId AtomicInteger traceId,
      @TraceIdKey Metadata.Key<String> traceIdMetadataKey,
      @ElapsedTimeKey Metadata.Key<String> elapsedTimeKey,
      Ticker ticker) {
    this.criticalPaths = criticalPaths;
    this.criticalPathKey = criticalPathKey;
    this.traceId = traceId;
    this.traceIdMetadataKey = traceIdMetadataKey;
    this.elapsedTimeKey = elapsedTimeKey;
    this.ticker = ticker;
  }

  @Override
  public <RequestT, ResponseT> Listener<RequestT> interceptCall(
      ServerCall<RequestT, ResponseT> call,
      Metadata requestHeaders,
      ServerCallHandler<RequestT, ResponseT> next) {
    // Stopwatch doesn't work - it only reads on its own process, not the global time elapsed.
    long startNanos = ticker.read();
    int serverSpan;
    if (requestHeaders.containsKey(traceIdMetadataKey)) {
      // This is a downstream server.
      serverSpan = Integer.parseInt(requestHeaders.get(traceIdMetadataKey));
    } else {
      serverSpan = traceId.incrementAndGet();
    }
    Context ctx = Context.current().withValue(Constants.TRACE_ID_CTX_KEY, serverSpan);
    return Contexts.interceptCall(
        ctx,
        new ForwardingServerCall.SimpleForwardingServerCall<RequestT, ResponseT>(call) {
          @Override
          public void sendHeaders(Metadata responseHeaders) {
            responseHeaders.put(elapsedTimeKey, Long.toString(ticker.read() - startNanos));
            // If this is from the frontend, we have no critical path to send.
            if (criticalPaths.get(serverSpan) != null) {
              responseHeaders.put(criticalPathKey, criticalPaths.get(serverSpan).toCriticalPath().toByteArray());
            }
            super.sendHeaders(responseHeaders);
          }
        },
        requestHeaders,
        next);
  }
}
