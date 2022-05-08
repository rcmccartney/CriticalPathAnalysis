package kvprog.client;

import dagger.BindsInstance;
import dagger.Component;
import io.grpc.ManagedChannel;
import org.kohsuke.args4j.*;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The main app responsible for running the client.
 */
public class KvClientApp {
  @Option(name = "-h", usage = "print help dialogue", help = true)
  private boolean help;

  @Option(name = "-t", usage = "server target address", metaVar = "TARGET")
  private String target = "localhost";

  @Option(name = "-p", usage = "port number of server", metaVar = "PORT")
  private String port = "30428";

  @Argument
  private List<String> arguments = new ArrayList<String>();

  /**
   * Main launches the client from the command line.
   */
  public static void main(String[] args) throws Exception {
    KvClientApp kvApp = new KvClientApp();
    CmdLineParser parser = new CmdLineParser(kvApp);
    try {
      parser.parseArgument(args);
      if (kvApp.arguments.isEmpty()) {
        throw new CmdLineException(parser, "No arguments given.");
      }
    } catch (CmdLineException e) {
      System.err.println(e);
      printHelp(parser);
      System.exit(1);
    }

    if (kvApp.help || kvApp.arguments.size() == 0) {
      printHelp(parser);
      return;
    }

    KvClient kvClient = DaggerKvClientApp_KvClient.builder().target(kvApp.target).port(kvApp.port).build();
    KvProgClient client = kvClient.client();
    ManagedChannel channel = kvClient.channel();

    try {
      if (kvApp.arguments.size() == 1) {
        client.get(kvApp.arguments.get(0));
      } else {
        client.put(kvApp.arguments.get(0), kvApp.arguments.get(1));
      }
    } finally {
      // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
      // resources the channel should be shut down when it will no longer be used. If it may be used
      // again leave it running.
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static void printHelp(CmdLineParser parser) {
    parser.printUsage(System.err);
    System.err.println();
    System.err.println("  Example: ./build/install/mygrpc/bin/kv-prog-client" + parser.printExample(OptionHandlerFilter.REQUIRED) + " key [value]");
  }

  @Singleton
  @Component(modules = {ClientModule.class})
  public interface KvClient {
    KvProgClient client();

    ManagedChannel channel();

    @Component.Builder
    interface Builder {
      @BindsInstance
      Builder target(@ClientModule.ServerTarget String target);

      @BindsInstance
      Builder port(@ClientModule.ServerPort String port);

      KvClient build();
    }
  }
}
