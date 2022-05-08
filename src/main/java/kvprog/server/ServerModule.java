package kvprog.server;

import dagger.Module;
import dagger.Provides;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Module
interface ServerModule {

  @Provides
  static Server provideServer(@Port String port) {
    return ServerBuilder.forPort(Integer.valueOf(port))
        .addService(new KvStoreImpl())
        .build();
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Port {}
}
