package kvprog.server;

import dagger.Module;
import dagger.Provides;
import io.grpc.Server;
import io.grpc.ServerBuilder;

@Module
interface ServerModule {

  @Provides
  static Server provideServer() {
    int port = 30428;
    return ServerBuilder.forPort(port)
        .addService(new KvStoreImpl())
        .build();
  }
}
