package kvprog.common;

import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import javax.inject.Inject;

/**
 * Sets up and tears down the producer's name into the {@link ComponentProducerTokenContext}.
 */
final class ThreadLocalComponentMonitor extends ProductionComponentMonitor {

  private final String componentName;

  ThreadLocalComponentMonitor(String componentName) {
    this.componentName = componentName;
  }

  @Override
  public ProducerMonitor producerMonitorFor(ProducerToken token) {
    return new ThreadLocalProducerMonitor(componentName, token);
  }

  static final class Factory extends ProductionComponentMonitor.Factory {

    private final Namer componentNamer;

    @Inject
    Factory(Namer componentNamer) {
      this.componentNamer = componentNamer;
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new ThreadLocalComponentMonitor(componentNamer.getName(component));
    }
  }

  static final class ThreadLocalProducerMonitor extends ProducerMonitor {

    private final ComponentProducerToken token;

    ThreadLocalProducerMonitor(String componentName, ProducerToken token) {
      this.token =
          ComponentProducerToken.builder().setComponentName(componentName).setProducerToken(token).build();
    }

    @Override
    public void methodStarting() {
      ComponentProducerTokenContext.set(token);
    }

    @Override
    public void methodFinished() {
      ComponentProducerTokenContext.remove();
    }
  }
}