package kvprog.cserver;

import io.grpc.Server;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.inject.Inject;

public class CServer {

  private static final Logger logger = Logger.getLogger(CServer.class.getName());

  private final Server server;

  @Inject
  CServer(Server server) {
    this.server = server;
  }

  void start() throws IOException {
    server.start();
    logger.info("C server started, listening on " + server.getPort());
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** C shutting down gRPC server since JVM is shutting down");
        try {
          CServer.this.stop();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.err.println("*** C server shut down ***");
      }
    });
  }

  void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }
}
