package kvprog;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KvProgClient {
    private static final Logger logger = Logger.getLogger(KvProgClient.class.getName());

    private final KvStoreGrpc.KvStoreBlockingStub blockingStub;

    /** Construct client for accessing server using the existing channel. */
    public KvProgClient(Channel channel) {
        // 'channel' here is a Channel, not a ManagedChannel, so it is not this code's responsibility to
        // shut it down.

        // Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
        blockingStub = KvStoreGrpc.newBlockingStub(channel);
    }

    public void put(String key, String value) {
        logger.info("Will try to put " + key + " to value " + value + " ...");
        PutRequest request = PutRequest.newBuilder().setKey(key).setValue(value).build();
        PutReply response;
        try {
            response = blockingStub.put(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Response: " + response.getStatus());
    }

    public void get(String key) {
        logger.info("Will try to get " + key + " ...");
        GetRequest request = GetRequest.newBuilder().setKey(key).build();
        GetReply response;
        try {
            response = blockingStub.get(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        if (response.hasValue()) {
            logger.info("Response: " + response.getValue());
        } else {
            logger.info("Lookup failed: " + response.getFailure());
        }
    }

    public static void main(String[] args) throws Exception {
        // Access a service running on the local machine on port 30428
        String target = "localhost:30428";
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: key [value]");
            System.exit(1);
        }

        // Create a communication channel to the server, known as a Channel. Channels are thread-safe
        // and reusable. It is common to create channels at the beginning of your application and reuse
        // them until the application shuts down.
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        try {
            KvProgClient client = new KvProgClient(channel);
            if (args.length == 1) {
                client.get(args[0]);
            } else {
                client.put(args[0], args[1]);
            }
        } finally {
            // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
            // resources the channel should be shut down when it will no longer be used. If it may be used
            // again leave it running.
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
