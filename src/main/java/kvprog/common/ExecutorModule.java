package kvprog.common;

import dagger.Module;
import dagger.Provides;
import dagger.producers.Production;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Module
public interface ExecutorModule {
  @Provides
  @Production
  static Executor executor() {
    return Executors.newCachedThreadPool();
  }
}