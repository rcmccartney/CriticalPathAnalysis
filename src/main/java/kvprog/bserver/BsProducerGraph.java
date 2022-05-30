package kvprog.bserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.grpc.server.CallScoped;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import kvprog.*;
import kvprog.common.ExecutorModule;
import kvprog.common.MonitorModule;

@CallScoped
@ProductionComponent(
    modules = {
        BsProducerGraph.BsProducerModule.class,
        ExecutorModule.class,
        MonitorModule.class,
    },
    dependencies = BsProducerGraph.Input.class)
interface BsProducerGraph {

  /**
   * Static factory method for {@link Input.Builder}
   */
  static Input.Builder builder() {
    return new AutoValue_BsProducerGraph_Input.Builder().setB1Request(
            B1Request.getDefaultInstance())
        .setB2Request(B2Request.getDefaultInstance());
  }

  ListenableFuture<B1Reply> b1();

  ListenableFuture<B2Reply> b2();

  @ProducerModule
  class BsProducerModule {

    @Produces
    static B1Reply b1(B1Request request, B2Reply reply) {
      System.err.println("In B1");
      return B1Reply.getDefaultInstance();
    }

    @Produces
    static B2Reply b2(C1Reply c1Reply) {
      System.err.println("In B2");
      return B2Reply.getDefaultInstance();
    }

    @Produces
    static ListenableFuture<C1Reply> callC1(CGrpc.CFutureStub stub, B2Request request) {
      return stub.c1(C1Request.getDefaultInstance());
    }
  }

  @AutoValue
  abstract class Input {

    abstract CGrpc.CFutureStub cStub();

    abstract B1Request b1Request();

    abstract B2Request b2Request();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setCStub(CGrpc.CFutureStub value);

      abstract Builder setB1Request(B1Request value);

      abstract Builder setB2Request(B2Request value);

      /**
       * Build the {@link BsProducerGraph}
       */
      final BsProducerGraph build() {
        return DaggerBsProducerGraph.builder()
            .input(autoBuild())
            .build();
      }
    }
  }
}
