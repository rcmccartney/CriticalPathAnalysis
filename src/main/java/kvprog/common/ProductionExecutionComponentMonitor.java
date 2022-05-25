package kvprog.common;

import com.google.common.collect.ImmutableList;
import dagger.grpc.server.CallScoped;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import javax.inject.Inject;

/**
 * Recorder (or request-scoped "monitor") that keeps track of which order producers are queued for
 * execution. This can be used to establish computational dependencies between producers execution
 * order as an implicit dependency.
 */
public final class ProductionExecutionComponentMonitor extends ProductionComponentMonitor {

  private final ImmutableList.Builder<GraphProducerToken> producers;
  private final String componentName;

  ProductionExecutionComponentMonitor(ImmutableList.Builder<GraphProducerToken> producers,
      String componentName) {
    this.producers = producers;
    this.componentName = componentName;
  }

  @Override
  public ProducerMonitor producerMonitorFor(ProducerToken token) {
    return new ProductionExecutionProducerMonitor(producers, componentName, token);
  }

  @CallScoped
  public static final class Factory extends ProductionComponentMonitor.Factory {

    private final ImmutableList.Builder<GraphProducerToken> producers;
    private final Names componentNames;

    @Inject
    Factory(Names componentNames) {
      this.producers = ImmutableList.builder();
      this.componentNames = componentNames;
    }

    /**
     * Returns the tokens of producers whose productions were requested by the specified producer.
     */
    public synchronized ImmutableList<GraphProducerToken> getExecutionOrder() {
      return producers.build();
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new ProductionExecutionComponentMonitor(producers, componentNames.getName(component));
    }
  }

  static final class ProductionExecutionProducerMonitor extends ProducerMonitor {

    private final ImmutableList.Builder<GraphProducerToken> producers;
    private final GraphProducerToken token;

    ProductionExecutionProducerMonitor(ImmutableList.Builder<GraphProducerToken> producers,
        String componentName, ProducerToken token) {
      this.producers = producers;
      this.token =
          GraphProducerToken.builder().setGraphName(componentName).setProducerToken(token)
              .setTokenName(Names.producerName(token)).build();
    }

    @Override
    public void methodStarting() {
      synchronized (producers) {
        producers.add(token);
      }
    }
  }
}
