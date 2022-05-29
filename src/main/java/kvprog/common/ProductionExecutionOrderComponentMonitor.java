package kvprog.common;

import com.google.common.collect.ImmutableList;
import dagger.grpc.server.CallScoped;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import javax.inject.Inject;

/**
 * Request-scoped monitor that keeps track of the order of execution of the producer graph, turning that execution
 * order into a flat list.  This can be used to establish computational dependencies between producers execution
 * order as an implicit dependency.
 */
public final class ProductionExecutionOrderComponentMonitor extends ProductionComponentMonitor {

  private final ImmutableList.Builder<ComponentProducerToken> producersOrderBuilder;
  private final String componentName;

  ProductionExecutionOrderComponentMonitor(ImmutableList.Builder<ComponentProducerToken> producersOrderBuilder,
                                           String componentName) {
    this.producersOrderBuilder = producersOrderBuilder;
    this.componentName = componentName;
  }

  @Override
  public ProducerMonitor producerMonitorFor(ProducerToken token) {
    return new ProductionExecutionProducerMonitor(producersOrderBuilder, componentName, token);
  }

  @CallScoped
  public static final class Factory extends ProductionComponentMonitor.Factory {

    private final ImmutableList.Builder<ComponentProducerToken> producersOrderBuilder;
    private final Namer componentNamer;

    @Inject
    Factory(Namer componentNames) {
      this.producersOrderBuilder = ImmutableList.builder();
      this.componentNamer = componentNames;
    }

    /**
     * Returns the execution order of Producer tokens from this Component.
     */
    public synchronized ImmutableList<ComponentProducerToken> getExecutionOrder() {
      return producersOrderBuilder.build();
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new ProductionExecutionOrderComponentMonitor(producersOrderBuilder, componentNamer.getName(component));
    }
  }

  static final class ProductionExecutionProducerMonitor extends ProducerMonitor {

    private final ImmutableList.Builder<ComponentProducerToken> producersOrderBuilder;
    private final ComponentProducerToken token;

    ProductionExecutionProducerMonitor(ImmutableList.Builder<ComponentProducerToken> producersOrderBuilder,
        String componentName, ProducerToken token) {
      this.producersOrderBuilder = producersOrderBuilder;
      this.token =
          ComponentProducerToken.builder().setComponentName(componentName).setProducerToken(token).build();
    }

    @Override
    public void methodStarting() {
      synchronized (producersOrderBuilder) {
        producersOrderBuilder.add(token);
      }
    }
  }
}
