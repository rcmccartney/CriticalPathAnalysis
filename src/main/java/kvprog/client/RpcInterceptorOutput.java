package kvprog.client;

import io.grpc.*;
import io.grpc.Metadata.Key;

import javax.inject.Inject;

public class RpcInterceptorOutput implements ClientInterceptor {

  @Inject
  RpcInterceptorOutput() {
  }

  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> methodDescriptor, final CallOptions callOptions,
      final Channel channel) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        channel.newCall(methodDescriptor, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata requestHeader) {
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener) {
          @Override
          public void onHeaders(Metadata responseHeader) {
            System.err.println("Client sees: " + responseHeader.get(Key.of("elapsed_time", Metadata.ASCII_STRING_MARSHALLER)));
            super.onHeaders(responseHeader);
          }
        }, requestHeader);
      }
    };
  }
}