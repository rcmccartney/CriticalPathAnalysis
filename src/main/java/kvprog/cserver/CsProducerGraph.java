package kvprog.cserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.grpc.server.CallScoped;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import kvprog.C1Reply;
import kvprog.C1Request;
import kvprog.C2Reply;
import kvprog.C2Request;
import kvprog.common.*;

import java.util.Map;

@CallScoped
@ProductionComponent(
    modules = {
        CsProducerGraph.CsProducerModule.class,
        ExecutorModule.class,
        MonitorModule.class,
    },
    dependencies = CsProducerGraph.Input.class)
interface CsProducerGraph {

  /**
   * Static factory method for {@link Input.Builder}
   */
  static Input.Builder builder() {
    return new AutoValue_CsProducerGraph_Input.Builder().setC1Request(
            C1Request.getDefaultInstance())
        .setC2Request(C2Request.getDefaultInstance());
  }

  ListenableFuture<C1Reply> c1();

  ListenableFuture<C2Reply> c2();

  @ProducerModule
  class CsProducerModule {

    @Produces
    static C1Reply c1(
        C1Request request,
        Map<Integer, CriticalPath> criticalPaths,
        CriticalPathSupplier supplier) {
      System.err.println("In C1");
      criticalPaths.put(Constants.TRACE_ID_CTX_KEY.get(), supplier.criticalPath());
      return C1Reply.getDefaultInstance();
    }

    @Produces
    static C2Reply c2(
        C1Reply c1Reply,
        C2Request c2Request,
        Map<Integer, CriticalPath> criticalPaths,
        CriticalPathSupplier supplier) {
      System.err.println("In C2");
      criticalPaths.put(Constants.TRACE_ID_CTX_KEY.get(), supplier.criticalPath());
      return C2Reply.getDefaultInstance();
    }
  }

  @AutoValue
  abstract class Input {
    abstract Map<Integer, CriticalPath> criticalPaths();

    abstract C1Request c1Request();

    abstract C2Request c2Request();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setCriticalPaths(Map<Integer, CriticalPath> value);

      abstract Builder setC1Request(C1Request value);

      abstract Builder setC2Request(C2Request value);

      /**
       * Build the {@link CsProducerGraph}
       */
      final CsProducerGraph build() {
        return DaggerCsProducerGraph.builder()
            .input(autoBuild())
            .build();
      }
    }
  }
}
