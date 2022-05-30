package kvprog.client;

import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kvprog.KvStoreGrpc;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Module
interface ClientModule {

  @Provides
  static KvStoreGrpc.KvStoreFutureStub provideClientStub(ManagedChannel channel) {
    return KvStoreGrpc.newFutureStub(channel);
  }

  @Singleton
  @Provides
  static ManagedChannel provideChannel(@ServerTarget String target,
                                       @ServerPort String port,
                                       LeafClientRpcInterceptor leafClientRpcInterceptor) {
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    return ManagedChannelBuilder.forTarget(String.format("%s:%s", target, port))
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .intercept(leafClientRpcInterceptor)
        .build();
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface ServerTarget {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface ServerPort {

  }
}
