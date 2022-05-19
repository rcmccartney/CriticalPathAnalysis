package kvprog.bserver;

import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.GrpcCallMetadataModule;
import kvprog.bserver.ServerApp.ServerComponent;

@Module(includes = BImplGrpcProxyModule.class)
class BComponentModule {

  @Provides
  static BImplServiceDefinition.Factory provideServiceFactor(
      final ServerComponent component) {
    return new BImplServiceDefinition.Factory() {
      @Override
      public BImplServiceDefinition grpcService(
          GrpcCallMetadataModule metadataModule) {
        return component.serviceComponent(metadataModule);
      }
    };
  }
}
