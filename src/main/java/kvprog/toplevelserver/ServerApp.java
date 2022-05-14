package kvprog.toplevelserver;

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
import kvprog.KvStoreGrpc;
import kvprog.common.RpcInterceptor;
import kvprog.common.InterceptorModule;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

import javax.inject.Singleton;

/**
 * The main app responsible for running the server.
 */
public class ServerApp {
  @Option(name = "-h", usage = "print help dialogue", help = true)
  private boolean help;

  @Option(name = "-p", usage = "port number of server", metaVar = "PORT")
  private String port = "9090";

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
    ServerComponent serverComponent = DaggerServerApp_ServerComponent.builder().nettyServerModule(NettyServerModule.bindingToPort(Integer.parseInt(app.port))).build();
    TopLevelServer server = serverComponent.server();
    server.start();
    server.blockUntilShutdown();
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println("  Example: ./build/install/mygrpc/bin/top-level-server" + parser.printExample(OptionHandlerFilter.REQUIRED));
  }

  @Singleton
  @Component(modules = {NettyServerModule.class, TopComponentModule.class, InterceptorModule.class})
  static abstract class ServerComponent {
    abstract TopLevelServer server();

    abstract ServiceComponent serviceComponent(GrpcCallMetadataModule metadataModule);

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
          RpcInterceptor interceptor) {
        return Arrays.asList(interceptor);
      }
    }
  }
}
