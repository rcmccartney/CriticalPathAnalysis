package kvprog.server;

import com.google.common.collect.Multiset;
import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.ForGrpcService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import kvprog.KvStoreGrpc;
import kvprog.server.TopComponentModule.CallData;

@Singleton
class CountingInterceptor implements ServerInterceptor {

  private final Multiset<String> calls;

  @Inject
  CountingInterceptor(@CallData Multiset<String> calls) {
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

  @Module
  static class CountingInterceptorModule {

    @Provides
    @ForGrpcService(KvStoreGrpc.class)
    static List<? extends ServerInterceptor> serviceInterceptors(
        CountingInterceptor countingInterceptor) {
      return Arrays.asList(countingInterceptor);
    }
  }
}
