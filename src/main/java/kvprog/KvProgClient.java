package kvprog;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

public class KvProgClient {
    private static final Logger logger = Logger.getLogger(KvProgClient.class.getName());

    private final KvStoreGrpc.KvStoreBlockingStub blockingStub;
    private final ManagedChannel channel;

    /** Construct client for accessing server using the existing channel. */
    @Inject
    public KvProgClient(ManagedChannel channel) {
        // TODO: Should use a Channel, not a ManagedChannel, so it is not this code's responsibility to
        // shut it down. Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
        this.channel = channel;
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

    public void shutdown() throws Exception {
      this.channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
}
