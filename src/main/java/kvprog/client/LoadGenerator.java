package kvprog.client;

import io.grpc.StatusRuntimeException;
import kvprog.CallInfo;
import kvprog.CallsReply;
import kvprog.CallsRequest;
import kvprog.KvStoreGrpc;
import kvprog.KvStoreGrpc.KvStoreBlockingStub;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoadGenerator {

  private static final Logger logger = Logger.getLogger(LoadGenerator.class.getName());

  private KvStoreGrpc.KvStoreFutureStub stub;

  /**
   * Construct client for accessing server using the existing channel.
   */
  @Inject
  public LoadGenerator(KvStoreGrpc.KvStoreFutureStub stub) {
    this.stub = stub;
  }

  public void put(String key, String value) {
    logger.info("Will try to put " + key + " to value " + value + " ...");
    ClientProductionComponent producers = DaggerClientProductionComponent
        .builder().input(ClientProductionComponent.Input.newBuilder().setStub(stub).setKey(key).setValue(value).build()).build();
    try {
      logger.info(producers.sendPut().get());
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  public void get(String key) {
    logger.info("Will try to get " + key + " ...");
    ClientProductionComponent producers = DaggerClientProductionComponent
        .builder().input(ClientProductionComponent.Input.newBuilder().setStub(stub).setKey(key).build()).build();
    try {
      logger.info(producers.sendGet().get());
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  public void callData() {
    logger.info("Fetching call data from server...");
    ClientProductionComponent producers = DaggerClientProductionComponent
        .builder().input(ClientProductionComponent.Input.newBuilder().setStub(stub).build()).build();
    try {
      logger.info(producers.callData().get());
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }
}
