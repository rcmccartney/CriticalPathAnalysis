package kvprog.cserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import javax.inject.Singleton;
import kvprog.C1Reply;
import kvprog.C1Request;
import kvprog.C2Reply;
import kvprog.C2Request;
import kvprog.common.ExecutorModule;

@Singleton
@ProductionComponent(
    modules = {
        ServerProducerGraph.ServerProducerModule.class,
        ExecutorModule.class,
    },
    dependencies = ServerProducerGraph.Input.class)
interface ServerProducerGraph {

  /**
   * Static factory method for {@link Input.Builder}
   */
  static Input.Builder builder() {
    return new AutoValue_ServerProducerGraph_Input.Builder().setC1Request(
            C1Request.getDefaultInstance())
        .setC2Request(C2Request.getDefaultInstance());
  }

  ListenableFuture<C1Reply> c1();

  ListenableFuture<C2Reply> c2();

  @ProducerModule
  class ServerProducerModule {

    @Produces
    static C1Reply c1(C1Request request) {
      System.err.println("In C1");
      return C1Reply.getDefaultInstance();
    }

    @Produces
    static C2Reply c2(C1Reply c1Reply, C2Request c2Request) {
      System.err.println("In C2");
      return C2Reply.getDefaultInstance();
    }
  }

  @AutoValue
  abstract class Input {

    abstract C1Request c1Request();

    abstract C2Request c2Request();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setC1Request(C1Request value);

      abstract Builder setC2Request(C2Request value);

      /**
       * Build the {@link ServerProducerGraph}
       */
      final ServerProducerGraph build() {
        return DaggerServerProducerGraph.builder()
            .input(autoBuild())
            .build();
      }
    }
  }
}
