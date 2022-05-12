package kvprog.client;

import dagger.Module;
import dagger.Provides;
import dagger.producers.Production;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Module
interface ExecutorModule {
  @Provides
  @Production
  static Executor executor() {
    return Executors.newCachedThreadPool();
  }
}