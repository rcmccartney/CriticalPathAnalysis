package kvprog.client;

import io.grpc.StatusRuntimeException;
import kvprog.CallInfo;
import kvprog.CallsReply;
import kvprog.CallsRequest;
import kvprog.GetReply;
import kvprog.GetRequest;
import kvprog.KvStoreGrpc.KvStoreBlockingStub;
import kvprog.PutReply;
import kvprog.PutRequest;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoadGenerator {

  private static final Logger logger = Logger.getLogger(LoadGenerator.class.getName());

  private final KvStoreBlockingStub blockingStub;

  /**
   * Construct client for accessing server using the existing channel.
   */
  @Inject
  public LoadGenerator(KvStoreBlockingStub blockingStub) {
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

  public void callData() {
    logger.info("Fetching call data from server...");
    CallsReply response;
    try {
      response = blockingStub.calls(CallsRequest.getDefaultInstance());
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return;
    }

    System.err.println("** Call Type : Count **");
    for (CallInfo info : response.getCallInfoList()) {
      System.err.println(String.format("%s : %s", info.getCallType(), info.getCount()));
    }
  }
}
