package kvprog.common;

import com.google.common.collect.Multiset;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
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
      Metadata headers,
      ServerCallHandler<RequestT, ResponseT> next) {
    calls.add(call.getMethodDescriptor().getFullMethodName());
    return next.startCall(call, headers);
  }
}

