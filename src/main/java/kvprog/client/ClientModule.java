package kvprog.client;

import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javax.inject.Singleton;
import kvprog.KvStoreGrpc;
import kvprog.KvStoreGrpc.KvStoreBlockingStub;

@Module
interface ClientModule {

  @Provides
  static KvStoreBlockingStub provideClientStub(ManagedChannel channel) {
    return KvStoreGrpc.newBlockingStub(channel);
  }

  @Singleton
  @Provides
  static ManagedChannel provideChannel() {
    // Access a service running on the local machine on port 30428
    String target = "localhost:30428";
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
    return channel;
  }
}
