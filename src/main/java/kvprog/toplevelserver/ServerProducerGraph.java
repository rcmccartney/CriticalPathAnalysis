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
import kvprog.common.Constants;
import kvprog.common.CriticalPathComponentMonitor;
import kvprog.common.ExecutorModule;
import kvprog.common.MonitorModule;

import javax.inject.Provider;
import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;

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

  @ProducerModule
  class ServerProducerModule {

    @Produces
    static PutReply put(
        PutRequest request,
        B1Reply b1Reply,
        @Conditional GetReply getReply,
        HashMap<String, String> cache) {
      System.err.println("In Put");
      PutReply reply;
      if (request.getKey().length() > 64 || request.getValue().length() > 512) {
        reply = PutReply.newBuilder().setStatus(Status.SYSTEMERR).build();
      } else {
        cache.put(request.getKey(), request.getValue());
        reply = PutReply.newBuilder().setStatus(Status.SUCCESS).build();
      }
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
        Provider<CriticalPathComponentMonitor.Factory> factory,
        GetRequest request,
        HashMap<String, String> cache,
        // TODO: make this conditional.
        B2Reply b2Reply,
        C1Reply c1Reply,
        C2Reply c2Reply) {
      System.err.println("In Get");
      GetReply reply;
      if (request.getKey().length() > 64) {
        reply = GetReply.newBuilder().setFailure(GetReply.Status.SYSTEMERR).build();
      } else if (!cache.containsKey(request.getKey())) {
        reply = GetReply.newBuilder().setFailure(GetReply.Status.NOTFOUND).build();
      } else {
        reply = GetReply.newBuilder().setValue(cache.get(request.getKey())).build();
      }
      System.err.println("Critical path `GET`: " + factory.get().criticalPath());
      return reply;
    }

    @Produces
    static ListenableFuture<B1Reply> callB1(BGrpc.BFutureStub stub, PutRequest request) {
      return stub.b1(B1Request.getDefaultInstance());
    }

    @Produces
    static ListenableFuture<B2Reply> callB2(BGrpc.BFutureStub stub, GetRequest request) {
      return stub.b2(B2Request.getDefaultInstance());
    }

    @Produces
    static ListenableFuture<C1Reply> callC1(CGrpc.CFutureStub stub, GetRequest request) {
      return stub.c1(C1Request.getDefaultInstance());
    }

    @Produces
    static ListenableFuture<C2Reply> callC2(CGrpc.CFutureStub stub, GetRequest request) {
      return stub.c2(C2Request.getDefaultInstance());
    }
  }

  @AutoValue
  abstract class Input {

    abstract BGrpc.BFutureStub bStub();

    abstract CGrpc.CFutureStub cStub();

    abstract HashMap<String, String> cache();

    abstract GetRequest getRequest();

    abstract PutRequest putRequest();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setBStub(BGrpc.BFutureStub value);

      abstract Builder setCStub(CGrpc.CFutureStub value);

      abstract Builder setCache(HashMap<String, String> value);

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
