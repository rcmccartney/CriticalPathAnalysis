package kvprog.client;

import com.google.common.collect.Multiset;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.inject.Inject;
import kvprog.KvStoreGrpc;

public class LoadGenerator {

  private static final Logger logger = Logger.getLogger(LoadGenerator.class.getName());

  private final KvStoreGrpc.KvStoreFutureStub stub;
  private final Multiset<String> calls;

  /**
   * Construct client for accessing server using the existing channel.
   */
  @Inject
  public LoadGenerator(KvStoreGrpc.KvStoreFutureStub stub,
      @ClientModule.CallMetadata Multiset<String> calls) {
    this.stub = stub;
    this.calls = calls;
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

  public void callData() {
    logger.info("Fetching call data from server...");
    ClientProducerGraph producers = ClientProducerGraph
        .builder().setStub(stub).build();
    try {
      logger.info(producers.callData().get());
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    logger.info("Fetching call data from RPC metadata...");
    for (String call : calls) {
      logger.info(call);
    }
  }
}
