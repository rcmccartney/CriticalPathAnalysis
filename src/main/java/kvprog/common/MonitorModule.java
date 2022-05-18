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
      ThreadLocalComponentMonitor.Factory monitorFactory) {
    return monitorFactory;
  }
}
