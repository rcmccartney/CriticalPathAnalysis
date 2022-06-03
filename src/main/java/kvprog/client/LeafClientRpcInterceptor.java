package kvprog.client;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import io.grpc.*;
import kvprog.common.InterceptorModule;

import javax.inject.Inject;


public class LeafClientRpcInterceptor implements ClientInterceptor {
  private final Metadata.Key<String> elapsedTimeKey;
  private final Ticker ticker;

  @Inject
  LeafClientRpcInterceptor(@InterceptorModule.ElapsedTimeKey Metadata.Key<String> elapsedTimeKey, Ticker ticker) {
    this.elapsedTimeKey = elapsedTimeKey;
    this.ticker = ticker;
  }

  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> methodDescriptor,
      final CallOptions callOptions,
      final Channel channel) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        channel.newCall(methodDescriptor, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata requestHeader) {
        long startNanos = ticker.read();
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener) {
          @Override
          public void onHeaders(Metadata responseHeader) {
            System.err.println("Leaf client request time from ticker: " + (ticker.read() - startNanos) + " nanos.");
            System.err.println("Leaf client sees from metadata: " + Integer.parseInt(responseHeader.get(elapsedTimeKey)) + " nanos.");
            super.onHeaders(responseHeader);
          }
        }, requestHeader);
      }
    };
  }
}
