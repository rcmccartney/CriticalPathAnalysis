package kvprog.bserver;

import dagger.Module;
import dagger.*;
import dagger.grpc.server.CallScoped;
import dagger.grpc.server.ForGrpcService;
import dagger.grpc.server.GrpcCallMetadataModule;
import dagger.grpc.server.NettyServerModule;
import io.grpc.ServerInterceptor;
import kvprog.BGrpc;
import kvprog.common.InterceptorModule;
import kvprog.common.RpcInterceptor;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

/**
 * The main app responsible for running the server.
 */
public class ServerApp {

  @Option(name = "-h", usage = "print help dialogue", help = true)
  private boolean help;

  @Option(name = "-p", usage = "port number of server", metaVar = "PORT")
  private String port = "30429";

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
        .cTarget(app.cTarget)
        .cPort(app.cPort)
        .build();
    BServer server = serverComponent.server();
    server.start();
    server.blockUntilShutdown();
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println(
        "  Example: ./build/install/mygrpc/bin/b-server" + parser.printExample(
            OptionHandlerFilter.REQUIRED));
  }

  @Singleton
  @Component(modules = {NettyServerModule.class, BComponentModule.class, InterceptorModule.class, BackendModule.class})
  static abstract class ServerComponent {

    abstract BServer server();

    abstract ServiceComponent serviceComponent(GrpcCallMetadataModule metadataModule);

    @Component.Builder
    interface Builder {
      Builder nettyServerModule(NettyServerModule module);

      @BindsInstance
      Builder cTarget(@BackendModule.CServerTarget String target);

      @BindsInstance
      Builder cPort(@BackendModule.CServerPort String port);

      ServerComponent build();
    }

    @CallScoped
    @Subcomponent(modules = {
        BImplGrpcServiceModule.class,
        GrpcCallMetadataModule.class,
        BInterceptorModule.class
    })
    interface ServiceComponent extends BImplServiceDefinition {
    }

    @Module
    static class BInterceptorModule {

      @Provides
      @ForGrpcService(BGrpc.class)
      static List<? extends ServerInterceptor> serviceInterceptors(
          RpcInterceptor interceptor) {
        return Arrays.asList(interceptor);
      }
    }
  }
}
