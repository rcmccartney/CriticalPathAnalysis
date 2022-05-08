package kvprog.server;

import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.ForGrpcService;
import io.grpc.ServerInterceptor;
import kvprog.KvStoreGrpc;

import java.util.Arrays;
import java.util.List;

@Module
class ServiceModule {
  @Provides
  @ForGrpcService(KvStoreGrpc.class)
  static List<? extends ServerInterceptor> serviceInterceptors() {
    return Arrays.asList();
  }
}
