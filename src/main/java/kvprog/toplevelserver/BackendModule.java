package kvprog.toplevelserver;

import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kvprog.BGrpc;
import kvprog.CGrpc;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Module
interface BackendModule {

  @Provides
  static BGrpc.BFutureStub provideBClientStub(@BManagedChannel ManagedChannel channel) {
    return BGrpc.newFutureStub(channel);
  }

  @Singleton
  @Provides
  @BManagedChannel
  static ManagedChannel provideBChannel(@BServerTarget String target, @BServerPort String port) {
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    return ManagedChannelBuilder.forTarget(String.format("%s:%s", target, port))
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
  }

  @Provides
  static CGrpc.CFutureStub provideCClientStub(@CManagedChannel ManagedChannel channel) {
    return CGrpc.newFutureStub(channel);
  }

  @Singleton
  @Provides
  @CManagedChannel
  static ManagedChannel provideCChannel(@CServerTarget String target, @CServerPort String port) {
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
  @interface BServerTarget {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface BServerPort {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface BManagedChannel {

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

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface CManagedChannel {

  }
}