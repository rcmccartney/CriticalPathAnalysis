package kvprog.client;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Qualifier;
import javax.inject.Singleton;

@Singleton
@ProductionComponent(
    modules = {
        ClientProductionComponent.ClientProducerModule.class,
        ClientProductionComponent.ExecutorModule.class,
    },
    dependencies = ClientProductionComponent.Input.class)
interface ClientProductionComponent {
  @Get ListenableFuture<String> get();

  @Put ListenableFuture<String> put();

  @Module
  final class ExecutorModule {
    @Provides
    @Production
    static Executor executor() {
      return Executors.newCachedThreadPool();
    }
  }

  @ProducerModule
  public class ClientProducerModule {

    @Produces
    @Get
    static ListenableFuture<String> get(Input input) {
      return Futures.immediateFuture("here");
    }

    @Produces
    @Put
    static ListenableFuture<String> put(Input input) {
      return Futures.immediateFuture("here");
    }
  }

  @AutoValue
  abstract static class Input {
    abstract String key();
    abstract String value();
    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setKey(String key);
      abstract Builder setValue(String value);
      abstract Input build();
    }
  }
}

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface Get {
}

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface Put {
}

