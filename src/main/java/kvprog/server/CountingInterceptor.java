package kvprog.server;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.ForGrpcService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import kvprog.KvStoreGrpc;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
class CountingInterceptor implements ServerInterceptor {
  private final Multiset<String> calls = ConcurrentHashMultiset.create();

  @Inject
  CountingInterceptor() {
  }

  @Override
  public <RequestT, ResponseT> Listener<RequestT> interceptCall(
      ServerCall<RequestT, ResponseT> call,
      Metadata headers,
      ServerCallHandler<RequestT, ResponseT> next) {

    System.err.println("Seen " + calls);

    calls.add(call.getMethodDescriptor().getFullMethodName());
    return next.startCall(call, headers);
  }

  public int countCalls(String methodName) {
    return calls.count(methodName);
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

