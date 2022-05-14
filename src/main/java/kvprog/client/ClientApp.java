package kvprog.client;

import dagger.BindsInstance;
import dagger.Component;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import kvprog.common.ExecutorModule;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

/**
 * The main app responsible for running the client.
 */
public class ClientApp {

  @Option(name = "-h", usage = "print help dialogue", help = true)
  private boolean help;

  @Option(name = "-t", usage = "server target address", metaVar = "TARGET")
  private String target = "localhost";

  @Option(name = "-p", usage = "port number of server", metaVar = "PORT")
  private String port = "9090";

  @Option(name = "-c", usage = "get call metadata from server")
  private boolean calls;

  @Argument
  private List<String> arguments = new ArrayList<String>();

  /**
   * Main launches the client from the command line.
   */
  public static void main(String[] args) throws Exception {
    ClientApp app = new ClientApp();
    CmdLineParser parser = new CmdLineParser(app);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e);
      printHelp(parser);
      System.exit(1);
    }

    if (app.help || (!app.calls && app.arguments.size() == 0) || app.arguments.size() > 2
        || (app.calls && app.arguments.size() != 0)) {
      printHelp(parser);
      return;
    }

    Client client = DaggerClientApp_Client.builder().target(app.target).port(app.port)
        .build();
    LoadGenerator loadGen = client.loadGen();

    try {
      if (app.calls) {
        loadGen.callData();
      } else if (app.arguments.size() == 1) {
        loadGen.get(app.arguments.get(0));
      } else {
        loadGen.put(app.arguments.get(0), app.arguments.get(1));
      }
    } finally {
      // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
      // resources the channel should be shut down when it will no longer be used. If it may be used
      // again leave it running.
      client.channel().shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
    }
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println("  Example: ./build/install/mygrpc/bin/client" + parser.printExample(
        OptionHandlerFilter.REQUIRED) + " key [value]");
  }

  @Singleton
  @Component(modules = {ClientModule.class, ExecutorModule.class})
  public interface Client {

    LoadGenerator loadGen();

    ManagedChannel channel();

    @Component.Builder
    interface Builder {

      @BindsInstance
      Builder target(@ClientModule.ServerTarget String target);

      @BindsInstance
      Builder port(@ClientModule.ServerPort String port);

      Client build();
    }
  }
}
