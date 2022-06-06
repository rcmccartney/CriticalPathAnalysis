package kvprog.toplevelserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.grpc.server.CallScoped;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import kvprog.*;
import kvprog.PutReply.Status;
import kvprog.common.*;
import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

@CallScoped
@ProductionComponent(
    modules = {
        ServerProducerGraph.ServerProducerModule.class,
        ExecutorModule.class,
        MonitorModule.class,
    },
    dependencies = ServerProducerGraph.Input.class)
interface ServerProducerGraph {

  /**
   * Static factory method for {@link Input.Builder}
   */
  static Input.Builder builder() {
    return new AutoValue_ServerProducerGraph_Input.Builder().setGetRequest(
            GetRequest.getDefaultInstance())
        .setPutRequest(PutRequest.getDefaultInstance());
  }

  ListenableFuture<GetReply> get();

  ListenableFuture<PutReply> put();

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface Conditional {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface SerialOrParallel {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface Serial {

  }
  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @interface Parallel {

  }

  @ProducerModule
  class ServerProducerModule {

    @Produces
    static PutReply put(
        PutRequest request,
        B1Reply b1Reply,
        @Conditional GetReply getReply,
        Map<String, String> cache,
        Map<Integer, InternalCriticalPath> criticalPaths,
        CriticalPathSupplier supplier) {
      PutReply reply;
      if (request.getKey().length() > 64 || request.getValue().length() > 512) {
        reply = PutReply.newBuilder().setStatus(Status.SYSTEMERR).build();
      } else {
        cache.put(request.getKey(), request.getValue());
        reply = PutReply.newBuilder().setStatus(Status.SUCCESS).build();
      }

      // TODO: ideally this would be in framework code, not application code.
      criticalPaths.put(Constants.TRACE_ID_CTX_KEY.get(), supplier.criticalPath());
      return reply;
    }

    @Produces
    @Conditional
    static ListenableFuture<GetReply> internalPut(PutRequest request, Producer<GetReply> getReply) {
      if (request.getKey().equals("queryOfDeath")) {
        System.err.println("Found slow query!");
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return getReply.get();
      } else {
        return Futures.immediateFuture(GetReply.getDefaultInstance());
      }
    }

    @Produces
    static GetReply get(
        GetRequest request,
        B2Reply b2Reply,
        C1Reply c1Reply,
        @SerialOrParallel C2Reply c2Reply,
        Map<String, String> cache,
        Map<Integer, InternalCriticalPath> criticalPaths,
        CriticalPathSupplier supplier) {
      GetReply reply;
      if (request.getKey().length() > 64) {
        reply = GetReply.newBuilder().setFailure(GetReply.Status.SYSTEMERR).build();
      } else if (!cache.containsKey(request.getKey())) {
        reply = GetReply.newBuilder().setFailure(GetReply.Status.NOTFOUND).build();
      } else {
        reply = GetReply.newBuilder().setValue(cache.get(request.getKey())).build();
      }

      // TODO: ideally this would be in framework code, not application code.
      criticalPaths.put(Constants.TRACE_ID_CTX_KEY.get(), supplier.criticalPath());
      return reply;
    }

    @Produces
    @SerialOrParallel
    static ListenableFuture<C2Reply> callC2SerialOrParallel(
        GetRequest request, @Parallel Producer<C2Reply> parallelC2Reply, @Serial Producer<C2Reply> serialC2Reply) {
      if (request.getKey().equals("CallC2InSeries")) {
        return serialC2Reply.get();
      } else {
        return parallelC2Reply.get();
      }
    }

    @Produces
    static ListenableFuture<B1Reply> callB1(BGrpc.BFutureStub stub, PutRequest request) {
      return stub.b1(B1Request.getDefaultInstance());
    }

    @Produces
    static ListenableFuture<B2Reply> callB2(BGrpc.BFutureStub stub, GetRequest request) {
      return stub.b2(B2Request.newBuilder().setCallC(true).build());
    }

    @Produces
    static ListenableFuture<C1Reply> callC1(CGrpc.CFutureStub stub, GetRequest request) {
      return stub.c1(C1Request.getDefaultInstance());
    }

    @Produces
    @Parallel
    static ListenableFuture<C2Reply> callC2InParallel(CGrpc.CFutureStub stub, GetRequest request) {
      return stub.c2(C2Request.getDefaultInstance());
    }

    @Produces
    @Serial
    static ListenableFuture<C2Reply> callC2InSeries(CGrpc.CFutureStub stub, B2Reply b2Reply, GetRequest request) {
      // Slow path.
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return stub.c2(C2Request.getDefaultInstance());
    }
  }

  @AutoValue
  abstract class Input {

    abstract BGrpc.BFutureStub bStub();

    abstract CGrpc.CFutureStub cStub();

    abstract Map<String, String> cache();

    abstract Map<Integer, InternalCriticalPath> criticalPaths();

    abstract GetRequest getRequest();

    abstract PutRequest putRequest();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setBStub(BGrpc.BFutureStub value);

      abstract Builder setCStub(CGrpc.CFutureStub value);

      abstract Builder setCache(Map<String, String> value);

      abstract Builder setCriticalPaths(Map<Integer, InternalCriticalPath> value);

      abstract Builder setGetRequest(GetRequest value);

      abstract Builder setPutRequest(PutRequest value);

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
