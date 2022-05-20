package kvprog.cserver;

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
import kvprog.CGrpc;
import kvprog.common.InterceptorModule;
import kvprog.common.RpcInterceptor;
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
  private String port = "30430";

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
        .nettyServerModule(NettyServerModule.bindingToPort(Integer.parseInt(app.port))).build();
    CServer server = serverComponent.server();
    server.start();
    server.blockUntilShutdown();
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println("  Example: ./build/install/mygrpc/bin/c-server" + parser.printExample(
        OptionHandlerFilter.REQUIRED));
  }

  @Singleton
  @Component(modules = {NettyServerModule.class, CComponentModule.class, InterceptorModule.class})
  static abstract class ServerComponent {

    abstract CServer server();

    abstract ServiceComponent serviceComponent(GrpcCallMetadataModule metadataModule);

    @CallScoped
    @Subcomponent(modules = {
        CImplGrpcServiceModule.class,
        GrpcCallMetadataModule.class,
        CInterceptorModule.class
    })
    interface ServiceComponent extends CImplServiceDefinition {

    }

    @Module
    static class CInterceptorModule {

      @Provides
      @ForGrpcService(CGrpc.class)
      static List<? extends ServerInterceptor> serviceInterceptors(
          RpcInterceptor interceptor) {
        return Arrays.asList(interceptor);
      }
    }
  }
}
