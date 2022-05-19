package kvprog.cserver;

import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.GrpcCallMetadataModule;
import kvprog.cserver.ServerApp.ServerComponent;

@Module(includes = CImplGrpcProxyModule.class)
class CComponentModule {

  @Provides
  static CImplServiceDefinition.Factory provideServiceFactor(
      final ServerComponent component) {
    return new CImplServiceDefinition.Factory() {
      @Override
      public CImplServiceDefinition grpcService(
          GrpcCallMetadataModule metadataModule) {
        return component.serviceComponent(metadataModule);
      }
    };
  }
}
