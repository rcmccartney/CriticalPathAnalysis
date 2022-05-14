package kvprog.client;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import kvprog.KvStoreGrpc;

@Module
interface ClientModule {

  @Provides
  static KvStoreGrpc.KvStoreFutureStub provideClientStub(ManagedChannel channel) {
    return KvStoreGrpc.newFutureStub(channel);
  }

  @Singleton // Shared between all requests.
  @Provides
  @CallMetadata
  static Multiset<String> provideRequestData() {
    return ConcurrentHashMultiset.create();
  }

  @Singleton
  @Provides
  static ManagedChannel provideChannel(@ServerTarget String target, @ServerPort String port,
      RpcInterceptorOutput rpcInterceptorOutput) {
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    return ManagedChannelBuilder.forTarget(String.format("%s:%s", target, port))
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .intercept(rpcInterceptorOutput)
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

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface CallMetadata {

  }
}
