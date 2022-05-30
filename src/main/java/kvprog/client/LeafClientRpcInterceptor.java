package kvprog.client;

import io.grpc.*;
import kvprog.common.InterceptorModule;

import javax.inject.Inject;

public class LeafClientRpcInterceptor implements ClientInterceptor {
  private final Metadata.Key<String> elapsedTimeKey;

  @Inject
  LeafClientRpcInterceptor(@InterceptorModule.ElapsedTimeKey Metadata.Key<String> elapsedTimeKey) {
    this.elapsedTimeKey = elapsedTimeKey;
  }

  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> methodDescriptor,
      final CallOptions callOptions,
      final Channel channel) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        channel.newCall(methodDescriptor, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata requestHeader) {
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener) {
          @Override
          public void onHeaders(Metadata responseHeader) {
            System.err.println("Leaf client sees: " + Integer.parseInt(responseHeader.get(elapsedTimeKey)) + " nanos.");
            super.onHeaders(responseHeader);
          }
        }, requestHeader);
      }
    };
  }
}
