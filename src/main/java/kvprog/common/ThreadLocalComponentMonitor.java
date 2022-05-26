package kvprog.common;

import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import javax.inject.Inject;

/**
 * A monitor that sets up and tears down the producer's name in a thread-local.
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

    private final Names componentNames;

    @Inject
    Factory(Names componentNames) {
      this.componentNames = componentNames;
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new ThreadLocalComponentMonitor(componentNames.getName(component));
    }
  }

  static final class ThreadLocalProducerMonitor extends ProducerMonitor {

    private final GraphProducerToken token;

    ThreadLocalProducerMonitor(String componentName, ProducerToken token) {
      this.token =
          GraphProducerToken.builder().setGraphName(componentName).setProducerToken(token)
              .setTokenName(Names.producerName(token)).build();
    }

    @Override
    public void methodStarting() {
      // System.err.println("methodStarting: " + token);
    }

    @Override
    public void methodFinished() {
      // System.err.println("methodFinished: " + token);
    }
  }
}