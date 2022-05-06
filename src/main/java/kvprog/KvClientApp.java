package kvprog;

import dagger.Component;
import javax.inject.Singleton;

/** The main app responsible for running the client. */
public class KvClientApp {
  @Singleton
  @Component(
      modules = {
        ClientModule.class
      }
  )
  public interface KvClient {
    KvProgClient client();
  }

    /**
     * Main launches the client from the command line.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: key [value]");
            System.exit(1);
        }

        KvClient kvClient = DaggerKvClientApp_KvClient.builder().build();
        KvProgClient client = kvClient.client();

        try {
            if (args.length == 1) {
                client.get(args[0]);
            } else {
                client.put(args[0], args[1]);
            }
        } finally {
            // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
            // resources the channel should be shut down when it will no longer be used. If it may be used
            // again leave it running.
            client.shutdown();
        }
    }
}
