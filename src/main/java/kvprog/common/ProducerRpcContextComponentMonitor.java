package kvprog.common;

import dagger.grpc.server.CallScoped;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;

import javax.inject.Inject;

/**
 * A monitor that sets up and tears down the {@link ProducerRpcContext} for every Producer method.
 *
 * TODO: merge with {@code ThreadLocalComponentMonitor}.
 */
final class ProducerRpcContextComponentMonitor extends ProductionComponentMonitor {
  private final CriticalPathLedgerSupplier criticalPathLedgerSupplier;

  ProducerRpcContextComponentMonitor(CriticalPathLedgerSupplier criticalPathLedgerSupplier) {
    this.criticalPathLedgerSupplier = criticalPathLedgerSupplier;
  }

  @Override
  public ProducerMonitor producerMonitorFor(ProducerToken token) {
    return new RpcContextProducerMonitor(criticalPathLedgerSupplier, token);
  }

  @CallScoped
  static final class Factory extends ProductionComponentMonitor.Factory {
    private final CriticalPathLedgerSupplier criticalPathLedgerSupplier;

    @Inject
    Factory(CriticalPathLedgerSupplier criticalPathLedgerSupplier) {
      this.criticalPathLedgerSupplier = criticalPathLedgerSupplier;
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new ProducerRpcContextComponentMonitor(criticalPathLedgerSupplier);
    }
  }

  static final class RpcContextProducerMonitor extends ProducerMonitor {
    private final CriticalPathLedgerSupplier criticalPathLedgerSupplier;
    private final ProducerToken token;

    RpcContextProducerMonitor(CriticalPathLedgerSupplier criticalPathLedgerSupplier, ProducerToken token) {
      this.criticalPathLedgerSupplier = criticalPathLedgerSupplier;
      this.token = token;
    }

    @Override
    public void methodStarting() {
      ProducerRpcContext.setActiveRpcContext(
          ProducerRpcContext.builder()
              .setCriticalPathLedgerSupplier(criticalPathLedgerSupplier)
              .setProducerName(Namer.producerName(token))
              .build());
    }

    @Override
    public void methodFinished() {
      ProducerRpcContext.clearActiveRpcContext();
    }
  }
}
