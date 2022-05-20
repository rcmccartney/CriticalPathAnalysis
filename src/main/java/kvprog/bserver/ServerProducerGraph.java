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
import kvprog.C1Reply;
import kvprog.C1Request;
import kvprog.CGrpc;
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
    static B1Reply b1(B1Request request, B2Reply reply) {
      System.err.println("In B1: B2Reply = " + reply);
      return B1Reply.getDefaultInstance();
    }

    @Produces
    static B2Reply b2(C1Reply c1Reply) {
      System.err.println("In B2: C1Reply = " + c1Reply);
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
