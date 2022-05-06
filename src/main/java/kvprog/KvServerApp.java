package kvprog;

import dagger.Component;
import javax.inject.Singleton;

/** The main app responsible for running the server. */
public class KvServerApp {
  @Singleton
  @Component(
      modules = {
        // ServerModule.class
      }
  )
  public interface KvServer {
    KvProgServer server();
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws Exception {
    KvServer kvServer = DaggerKvServerApp_KvServer.builder().build();
    KvProgServer server = kvServer.server();
    server.start();
    server.blockUntilShutdown();
  }
}
