package kvprog.toplevelserver;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.grpc.server.CallScoped;
import dagger.grpc.server.ForGrpcService;
import dagger.grpc.server.GrpcCallMetadataModule;
import dagger.grpc.server.NettyServerModule;
import io.grpc.ServerInterceptor;
import java.util.Arrays;
import java.util.List;
import javax.inject.Singleton;
import kvprog.KvStoreGrpc;
import kvprog.common.InterceptorModule;
import kvprog.common.ServerRpcInterceptor;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

/**
 * The main app responsible for running the server.
 */
public class ServerApp {

  @Option(name = "-h", usage = "print help dialogue", help = true)
  private boolean help;

  @Option(name = "-p", usage = "port number of server", metaVar = "PORT")
  private String port = "9090";

  @Option(name = "-tb", usage = "B server target address", metaVar = "B_TARGET")
  private String bTarget = "localhost";

  @Option(name = "-pb", usage = "port number of server B", metaVar = "B_PORT")
  private String bPort = "30429";

  @Option(name = "-tc", usage = "C server target address", metaVar = "C_TARGET")
  private String cTarget = "localhost";

  @Option(name = "-pc", usage = "port number of server C", metaVar = "C_PORT")
  private String cPort = "30430";

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws Exception {
    ServerApp app = new ServerApp();
    CmdLineParser parser = new CmdLineParser(app);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e);
      printHelp(parser);
      System.exit(1);
    }

    if (app.help) {
      printHelp(parser);
      return;
    }

    ServerComponent serverComponent = DaggerServerApp_ServerComponent.builder()
        .nettyServerModule(NettyServerModule.bindingToPort(Integer.parseInt(app.port)))
        .bTarget(app.bTarget)
        .bPort(app.bPort)
        .cTarget(app.cTarget)
        .cPort(app.cPort)
        .build();
    TopLevelServer server = serverComponent.server();
    server.start();
    server.blockUntilShutdown();
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println(
        "  Example: ./build/install/mygrpc/bin/top-level-server" + parser.printExample(
            OptionHandlerFilter.REQUIRED));
  }

  @Singleton
  @Component(modules = {NettyServerModule.class, TopComponentModule.class, InterceptorModule.class,
      BackendModule.class})
  static abstract class ServerComponent {

    abstract TopLevelServer server();

    abstract ServiceComponent serviceComponent(GrpcCallMetadataModule metadataModule);

    @Component.Builder
    interface Builder {

      Builder nettyServerModule(NettyServerModule module);

      @BindsInstance
      Builder bTarget(@BackendModule.BServerTarget String target);

      @BindsInstance
      Builder bPort(@BackendModule.BServerPort String port);

      @BindsInstance
      Builder cTarget(@BackendModule.CServerTarget String target);

      @BindsInstance
      Builder cPort(@BackendModule.CServerPort String port);

      ServerComponent build();
    }

    @CallScoped
    @Subcomponent(modules = {
        KvStoreImplGrpcServiceModule.class,
        GrpcCallMetadataModule.class,
        KvStoreInterceptorModule.class
    })
    interface ServiceComponent extends KvStoreImplServiceDefinition {

    }

    @Module
    static class KvStoreInterceptorModule {

      @Provides
      @ForGrpcService(KvStoreGrpc.class)
      static List<? extends ServerInterceptor> serviceInterceptors(
          ServerRpcInterceptor interceptor) {
        return Arrays.asList(interceptor);
      }
    }
  }
}
