package kvprog.bserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import javax.inject.Singleton;

import kvprog.B1Reply;
import kvprog.B1Request;
import kvprog.B2Reply;
import kvprog.B2Request;
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
    return new AutoValue_ServerProducerGraph_Input.Builder().setB1Request(
            B1Request.getDefaultInstance())
        .setB2Request(B2Request.getDefaultInstance());
  }

  ListenableFuture<B1Reply> b1();

  ListenableFuture<B2Reply> b2();

  @ProducerModule
  class ServerProducerModule {

    @Produces
    static B1Reply b1(B1Request request) {
      return B1Reply.getDefaultInstance();
    }

    @Produces
    static B2Reply b2(B2Request request) {
      return B2Reply.getDefaultInstance();
    }
  }

  @AutoValue
  abstract class Input {

    abstract B1Request b1Request();

    abstract B2Request b2Request();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setB1Request(B1Request value);

      abstract Builder setB2Request(B2Request value);

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
