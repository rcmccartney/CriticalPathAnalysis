package kvprog.server;

import dagger.Component;
import dagger.Subcomponent;
import dagger.grpc.server.CallScoped;
import dagger.grpc.server.GrpcCallMetadataModule;
import dagger.grpc.server.NettyServerModule;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

import javax.inject.Singleton;

/**
 * The main app responsible for running the server.
 */
public class KvServerApp {
  @Option(name = "-h", usage = "print help dialogue", help = true)
  private boolean help;

  @Option(name = "-p", usage = "port number of server", metaVar = "PORT")
  private String port = "30428";

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws Exception {
    KvServerApp kvApp = new KvServerApp();
    CmdLineParser parser = new CmdLineParser(kvApp);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e);
      printHelp(parser);
      System.exit(1);
    }

    if (kvApp.help) {
      printHelp(parser);
      return;
    }

    KvServerComponent kvServerComponent = DaggerKvServerApp_KvServerComponent.builder().nettyServerModule(NettyServerModule.bindingToPort(Integer.parseInt(kvApp.port))).build();
    KvProgServer server = kvServerComponent.server();
    server.start();
    server.blockUntilShutdown();
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println("  Example: ./build/install/mygrpc/bin/kv-prog-server" + parser.printExample(OptionHandlerFilter.REQUIRED));
  }

  @Singleton
  @Component(modules = {NettyServerModule.class, TopComponentModule.class})
  static abstract class KvServerComponent {
    abstract KvProgServer server();

    abstract KvServiceComponent serviceComponent(GrpcCallMetadataModule metadataModule);

    @CallScoped
    @Subcomponent(modules = {
        KvStoreImplGrpcServiceModule.class,
        GrpcCallMetadataModule.class,
        CountingInterceptor.CountingInterceptorModule.class
    })
    interface KvServiceComponent extends KvStoreImplServiceDefinition {
    }
  }
}
