package kvprog.common;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import dagger.producers.monitoring.ProductionComponentMonitor;

@Module
public class MonitorModule {

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

  @Provides
  @IntoSet
  static ProductionComponentMonitor.Factory criticalPathComponentMonitorFactory(
      CriticalPathComponentMonitor.Factory factory) {
    return factory;
  }
}
