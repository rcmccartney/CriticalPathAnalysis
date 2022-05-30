package kvprog.common;

import com.google.auto.value.AutoValue;

import java.util.logging.Logger;

/**
 * A class that holds information on the running context where an RPC is issued within a Producer
 * graph. An instance of this class is set in a thread-local whenever a Producer might send an RPC, and then
 * the context is retrieved in the gRPC interceptor.
 */
@AutoValue
public abstract class ProducerRpcContext {
  private static final ThreadLocal<ProducerRpcContext> activeContext = new ThreadLocal<>();
  private static final Logger logger = Logger.getLogger(ProducerRpcContext.class.getName());

  /**
   * Clears the existing context. Must be called on the same thread that executes {@link
   * #setActiveRpcContext}.
   */
  public static void clearActiveRpcContext() {
    activeContext.remove();
  }

  /**
   * Returns the current active context on the calling thread.
   */
  public static ProducerRpcContext getActiveRpcContext() {
    return activeContext.get();
  }

  /**
   * Sets the {@code ProducerRpcContext} to be active. Active context can be fetched via {@code
   * getActiveRpcContext} called from the same thread. Note: if current active context is not empty,
   * it will be discarded with a logged error.
   */
  public static void setActiveRpcContext(ProducerRpcContext context) {
    if (activeContext.get() != null) {
      logger.severe("Current context is not empty! Found: " + activeContext.get().producerName());
    } else {
      activeContext.set(context);
    }
  }

  public static Builder builder() {
    return new AutoValue_ProducerRpcContext.Builder();
  }

  /**
   * The identifier for this context, which is the Producers name.
   */
  public abstract String producerName();

  /**
   * The ledger supplier that creates a ledger for a given Producer.
   */
  public abstract CriticalPathLedgerSupplier criticalPathLedgerSupplier();

  @AutoValue.Builder
  abstract static class Builder {

    abstract ProducerRpcContext build();

    abstract Builder setProducerName(String value);

    abstract Builder setCriticalPathLedgerSupplier(CriticalPathLedgerSupplier value);
  }
}
