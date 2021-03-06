package kvprog.bserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.grpc.server.CallScoped;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import kvprog.*;
import kvprog.common.*;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

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

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface Conditional {

  }

  @ProducerModule
  class BsProducerModule {

    @Produces
    static B1Reply b1(
        B1Request request,
        B2Reply reply,
        Map<Integer, InternalCriticalPath> criticalPaths,
        CriticalPathSupplier supplier) {
      criticalPaths.put(Constants.TRACE_ID_CTX_KEY.get(), supplier.criticalPath());
      return B1Reply.getDefaultInstance();
    }

    @Produces
    static B2Reply b2(
        @Conditional C1Reply c1Reply,
        Map<Integer, InternalCriticalPath> criticalPaths,
        CriticalPathSupplier supplier) {
      criticalPaths.put(Constants.TRACE_ID_CTX_KEY.get(), supplier.criticalPath());
      return B2Reply.getDefaultInstance();
    }

    @Produces
    @Conditional
    static ListenableFuture<C1Reply> internalB2(B2Request request, Producer<C1Reply> c1Reply) {
      if (request.getCallC()) {
        return c1Reply.get();
      } else {
        return Futures.immediateFuture(C1Reply.getDefaultInstance());
      }
    }

    @Produces
    static ListenableFuture<C1Reply> callC1(CGrpc.CFutureStub stub, B2Request request) {
      return stub.c1(C1Request.getDefaultInstance());
    }
  }

  @AutoValue
  abstract class Input {

    abstract CGrpc.CFutureStub cStub();

    abstract Map<Integer, InternalCriticalPath> criticalPaths();

    abstract B1Request b1Request();

    abstract B2Request b2Request();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setCStub(CGrpc.CFutureStub value);

      abstract Builder setCriticalPaths(Map<Integer, InternalCriticalPath> value);

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
