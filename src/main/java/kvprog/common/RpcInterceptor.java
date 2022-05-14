package kvprog.common;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multiset;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import javax.inject.Inject;
import javax.inject.Singleton;
import kvprog.common.InterceptorModule.CallMetadata;

@Singleton
public class RpcInterceptor implements ServerInterceptor {

  private final Multiset<String> calls;

  @Inject
  RpcInterceptor(@CallMetadata Multiset<String> calls) {
    this.calls = calls;
  }

  @Override
  public <RequestT, ResponseT> Listener<RequestT> interceptCall(
      ServerCall<RequestT, ResponseT> call,
      Metadata requestHeaders,
      ServerCallHandler<RequestT, ResponseT> next) {
    calls.add(call.getMethodDescriptor().getFullMethodName());
    Stopwatch sw = Stopwatch.createStarted();
    return next.startCall(
        new ForwardingServerCall.SimpleForwardingServerCall<RequestT, ResponseT>(call) {
          @Override
          public void sendHeaders(Metadata responseHeaders) {
            responseHeaders.put(Key.of("elapsed_time", Metadata.ASCII_STRING_MARSHALLER),
                call.getMethodDescriptor().getFullMethodName() + ": " + sw.elapsed().getNano());
            super.sendHeaders(responseHeaders);
          }

          public void close(Status status, Metadata trailers) {
            super.close(status, trailers);
          }
        }, requestHeaders);
  }
}
