package kvprog.common;

import com.google.common.base.Stopwatch;
import io.grpc.*;
import io.grpc.ServerCall.Listener;
import kvprog.common.InterceptorModule.ElapsedTimeKey;
import kvprog.common.InterceptorModule.TraceId;
import kvprog.common.InterceptorModule.TraceIdKey;
import kvprog.common.InterceptorModule.CriticalPaths;
import kvprog.common.InterceptorModule.CostListKey;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ServerRpcInterceptor implements ServerInterceptor {

  private final Map<Integer, CriticalPath> criticalPaths;
  private final Metadata.Key<byte[]> costListKey;
  private final AtomicInteger traceId;
  private final Metadata.Key<String> traceIdMetadataKey;
  private final Metadata.Key<String> elapsedTimeKey;

  @Inject
  ServerRpcInterceptor(
      @CriticalPaths Map<Integer, CriticalPath> criticalPaths,
      @CostListKey Metadata.Key<byte[]> costListKey,
      @TraceId AtomicInteger traceId,
      @TraceIdKey Metadata.Key<String> traceIdMetadataKey,
      @ElapsedTimeKey Metadata.Key<String> elapsedTimeKey) {
    this.criticalPaths = criticalPaths;
    this.costListKey = costListKey;
    this.traceId = traceId;
    this.traceIdMetadataKey = traceIdMetadataKey;
    this.elapsedTimeKey = elapsedTimeKey;
  }

  @Override
  public <RequestT, ResponseT> Listener<RequestT> interceptCall(
      ServerCall<RequestT, ResponseT> call,
      Metadata requestHeaders,
      ServerCallHandler<RequestT, ResponseT> next) {
    Stopwatch sw = Stopwatch.createStarted();
    int serverSpan;
    if (requestHeaders.containsKey(traceIdMetadataKey)) {
      // This is a downstream server.
      serverSpan = Integer.parseInt(requestHeaders.get(traceIdMetadataKey));
      System.err.println("I'm downstream with span: " + serverSpan);
    } else {
      serverSpan = traceId.incrementAndGet();
      System.err.println("We're a top level server!");
    }
    System.err.println("Setting context with span: " + serverSpan);
    Context ctx = Context.current().withValue(Constants.TRACE_ID_CTX_KEY, serverSpan);
    return Contexts.interceptCall(
        ctx,
        new ForwardingServerCall.SimpleForwardingServerCall<RequestT, ResponseT>(call) {
          @Override
          public void sendHeaders(Metadata responseHeaders) {
            responseHeaders.put(elapsedTimeKey, Integer.toString(sw.elapsed().getNano()));
            System.err.println("Sending over: " + criticalPaths.get(serverSpan));
            responseHeaders.put(costListKey, criticalPaths.get(serverSpan).toCostList().toByteArray());
            super.sendHeaders(responseHeaders);
          }
        },
        requestHeaders,
        next);
  }
}
