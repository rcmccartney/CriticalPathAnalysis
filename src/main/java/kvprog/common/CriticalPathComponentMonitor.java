package kvprog.common;

import com.google.common.base.Ticker;
import dagger.grpc.server.CallScoped;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A monitor that calculates the critical path of execution.
 */
final class CriticalPathComponentMonitor extends ProductionComponentMonitor {
 private final String componentName;
  private final Ticker ticker;
  private final Map<ProducerToken, CriticalPathProducerMonitor> producerMonitors = new HashMap<>();

  // Records the producer that's currently running on the current thread.
  private static final ThreadLocal<ProducerToken> activeProducer = new ThreadLocal<>();

  CriticalPathComponentMonitor(String componentName, Ticker ticker) {
    this.componentName = componentName;
    this.ticker = ticker;
  }

  @Override
  public synchronized ProducerMonitor producerMonitorFor(ProducerToken token) {
    CriticalPathProducerMonitor monitor = new CriticalPathProducerMonitor(token);
    producerMonitors.put(token, monitor);
    return monitor;
  }

  synchronized long getTotalCpuUsec() {
    long cpuUsec = 0;
    for (CriticalPathProducerMonitor recorder : producerMonitors.values()) {
      cpuUsec += recorder.cpuUsec();
    }
    return cpuUsec;
  }

  /**
   * The factory that creates {@code StatsMonitor}. This is scoped because it collects data
   * throughout the request on behalf of each production component that's run.
   */
  @CallScoped
  static final class Factory extends ProductionComponentMonitor.Factory {
    private final Map<String, CriticalPathComponentMonitor> statsMonitors = new LinkedHashMap<>();
    private final ProductionExecutionComponentMonitor productionExecutionMonitor;
    private final Names componentNames;
    private final Ticker ticker;

    @Inject
    Factory(
        ProductionExecutionComponentMonitor productionExecutionMonitor,
        Names componentNames,
        Ticker ticker) {
      this.productionExecutionMonitor = productionExecutionMonitor;
      this.componentNames = componentNames;
      this.ticker = Ticker.systemTicker();
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      CriticalPathComponentMonitor componentMonitor =
          new CriticalPathComponentMonitor(componentNames.getName(component), ticker);
      statsMonitors.put(componentMonitor.componentName, componentMonitor);
      return componentMonitor;
    }
  }

  final class CriticalPathProducerMonitor extends ProducerMonitor {
    private final ProducerToken producerToken;

    // These variables are volatile so that updates to them on producer threads (e.g., stubby
    // threads) will be read by the main request thread.
    private volatile long readyNanos = -1;
    private volatile long startedNanos = -1;
    private volatile long durationNanos = -1;
    private volatile long completedNanos = -1;
    private volatile ProducerToken requester = null;

    CriticalPathProducerMonitor(ProducerToken producerToken) {
      this.producerToken = producerToken;
    }

    long readyTimeUsec() {
      return readyNanos / 1000;
    }

    long startTimeUsec() {
      return startedNanos / 1000;
    }

    ProducerToken token() {
      return producerToken;
    }

    long cpuUsec() {
      return Math.max(durationNanos / 1000, 0);
    }

    private synchronized void completeIfStillRunning() {
      if (startedNanos != -1 && completedNanos == -1) {
        completed();
      }
    }

    long latencyUsec() {
      if (startedNanos == -1) {
        return 0L;
      } else if (completedNanos == -1) {
        return (elapsedNanos() - startedNanos) / 1000;
      }
      return latencyNanos() / 1000;
    }

    long endTimeUsec() {
      if (startedNanos == -1) {
        return 0L;
      } else if (completedNanos == -1) {
        return elapsedNanos() / 1000;
      }
      return completedNanos / 1000;
    }

    @Override
    public synchronized void ready() {
      this.readyNanos = elapsedNanos();
    }

    @Override
    public void requested() {
      ProducerToken requester = activeProducer.get();
      if (requester != null) {
        this.requester = requester;
      }
    }

    Optional<ProducerToken> getRequester() {
      return Optional.ofNullable(requester);
    }

    @Override
    public synchronized void methodStarting() {
      this.startedNanos = elapsedNanos();
      activeProducer.set(producerToken);
    }

    @Override
    public synchronized void methodFinished() {
      this.durationNanos = elapsedNanos() - startedNanos;
      activeProducer.set(null);
    }

    private synchronized void completed() {
      this.completedNanos = elapsedNanos();
    }

    private long elapsedNanos() {
      return ticker.read();
    }

    private long latencyNanos() {
      completeIfStillRunning();
      return completedNanos - startedNanos;
    }

    @Override
    public void succeeded(Object o) {
      completeIfStillRunning();
    }

    @Override
    public void failed(Throwable t) {
      completeIfStillRunning();
    }
  }
}
