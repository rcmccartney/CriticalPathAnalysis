package kvprog.client;

import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import kvprog.KvStoreGrpc;
import kvprog.KvStoreGrpc.KvStoreBlockingStub;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Module
interface ClientModule {

  @Provides
  static KvStoreBlockingStub provideClientStub(ManagedChannel channel) {
    return KvStoreGrpc.newBlockingStub(channel);
  }

  @Singleton
  @Provides
  static ManagedChannel provideChannel(@ServerTarget String target, @ServerPort String port) {
    String serverAddr = String.format("%s:%s", target, port);
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    ManagedChannel channel = ManagedChannelBuilder.forTarget(serverAddr)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
    return channel;
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ServerTarget {}

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ServerPort {}
}
