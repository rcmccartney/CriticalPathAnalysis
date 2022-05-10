package kvprog.server;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.GrpcCallMetadataModule;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import javax.inject.Qualifier;
import javax.inject.Singleton;

@Module(includes = KvStoreImplGrpcProxyModule.class)
class TopComponentModule {

  @Provides
  static KvStoreImplServiceDefinition.Factory provideServiceFactor(
      final KvServerApp.KvServerComponent component) {
    return new KvStoreImplServiceDefinition.Factory() {
      @Override
      public KvStoreImplServiceDefinition grpcService(
          GrpcCallMetadataModule metadataModule) {
        return component.serviceComponent(metadataModule);
      }
    };
  }

  @Singleton // Shared between all requests.
  @Provides
  @CallData
  Multiset<String> provideRequestData() {
    return ConcurrentHashMultiset.create();
  }

  @Singleton // Shared between all requests.
  @Provides
  @Cache
  HashMap<String, String> provideCache() {
    return new HashMap<>();
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface CallData {}

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Cache {}
}
