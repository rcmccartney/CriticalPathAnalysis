package kvprog.client;

import io.grpc.StatusRuntimeException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import kvprog.GetReply;
import kvprog.GetRequest;
import kvprog.KvStoreGrpc.KvStoreBlockingStub;
import kvprog.PutReply;
import kvprog.PutRequest;

public class KvProgClient {

  private static final Logger logger = Logger.getLogger(KvProgClient.class.getName());

  private final KvStoreBlockingStub blockingStub;

  /**
   * Construct client for accessing server using the existing channel.
   */
  @Inject
  public KvProgClient(KvStoreBlockingStub blockingStub) {
    this.blockingStub = blockingStub;
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
}
