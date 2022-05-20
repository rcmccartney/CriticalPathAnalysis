package kvprog.bserver;

import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import kvprog.CGrpc;

@Module
interface BackendModule {

  @Provides
  static CGrpc.CFutureStub provideClientStub(ManagedChannel channel) {
    return CGrpc.newFutureStub(channel);
  }

  @Singleton
  @Provides
  static ManagedChannel provideChannel(@CServerTarget String target, @CServerPort String port) {
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    return ManagedChannelBuilder.forTarget(String.format("%s:%s", target, port))
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface CServerTarget {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface CServerPort {

  }
}