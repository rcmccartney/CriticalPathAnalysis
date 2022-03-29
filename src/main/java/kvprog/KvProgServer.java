package kvprog;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import kvprog.PutReply.Status;

public class KvProgServer {
    private static final Logger logger = Logger.getLogger(KvProgServer.class.getName());

    private Server server;

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 30428;
        server = ServerBuilder.forPort(port)
                .addService(new KvStoreImpl())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    KvProgServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final KvProgServer server = new KvProgServer();
        server.start();
        server.blockUntilShutdown();
    }

    static class KvStoreImpl extends KvStoreGrpc.KvStoreImplBase {
        private HashMap<String, String> cache = new HashMap<>();

        @Override
        public void put(PutRequest req, StreamObserver<PutReply> responseObserver) {
            PutReply reply;
            if (req.getKey().length() > 64 || req.getValue().length() > 512) {
                reply = PutReply.newBuilder().setStatus(Status.SYSTEMERR).build();
            } else {
                cache.put(req.getKey(), req.getValue());
                reply = PutReply.newBuilder().setStatus(Status.SUCCESS).build();
            }

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
        @Override
        public void get(GetRequest req, StreamObserver<GetReply> responseObserver) {
            GetReply reply;
            if (req.getKey().length() > 64) {
                reply = GetReply.newBuilder().setFailure(GetReply.Status.SYSTEMERR).build();
            } else if (!cache.containsKey(req.getKey())) {
                reply = GetReply.newBuilder().setFailure(GetReply.Status.NOTFOUND).build();
            } else {
                reply = GetReply.newBuilder().setValue(cache.get(req.getKey())).build();
            }

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
