package kvprog.client;

import kvprog.KvStoreGrpc;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class LoadGenerator {

  private static final Logger logger = Logger.getLogger(LoadGenerator.class.getName());

  private final KvStoreGrpc.KvStoreFutureStub stub;

  /**
   * Construct client for accessing server using the existing channel.
   */
  @Inject
  public LoadGenerator(KvStoreGrpc.KvStoreFutureStub stub) {
    this.stub = stub;
  }

  public void put(String key, String value) {
    logger.info("Will try to put " + key + " to value " + value + ".");
    ClientProducerGraph producers = ClientProducerGraph
        .builder().setStub(stub).setKey(key).setValue(value).build();
    try {
      logger.info(producers.sendPut().get());
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  public void get(String key) {
    logger.info("Will try to get " + key + ".");
    ClientProducerGraph producers = ClientProducerGraph
        .builder().setStub(stub).setKey(key).build();
    try {
      logger.info(producers.sendGet().get());
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }
}
