package kvprog.common;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.grpc.server.CallScoped;
import dagger.multibindings.IntoSet;
import dagger.producers.monitoring.ProductionComponentMonitor;

@Module
public abstract class MonitorModule {

  @Provides
  @IntoSet
  static ProductionComponentMonitor.Factory traceContextComponentMonitorFactory(
      ThreadLocalComponentMonitor.Factory factory) {
    return factory;
  }

  @Provides
  @IntoSet
  static ProductionComponentMonitor.Factory productionExecutionComponentMonitorFactory(
      ProductionExecutionOrderComponentMonitor.Factory factory) {
    return factory;
  }

  @CallScoped
  @Binds
  abstract CriticalPathSupplier bindCriticalPathSupplier(CriticalPathComponentMonitor.Factory factory);

  @Provides
  @IntoSet
  static ProductionComponentMonitor.Factory criticalPathComponentMonitorFactory(
      CriticalPathComponentMonitor.Factory factory) {
    return factory;
  }

  @Provides
  @IntoSet
  static ProductionComponentMonitor.Factory rpcContextComponentMonitorFactory(
      ProducerRpcContextComponentMonitor.Factory factory) {
    return factory;
  }
}
