package kvprog.bserver;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import java.util.HashMap;
import javax.inject.Singleton;
import kvprog.GetReply;
import kvprog.GetRequest;
import kvprog.PutReply;
import kvprog.PutReply.Status;
import kvprog.PutRequest;
import kvprog.common.ExecutorModule;
import kvprog.bserver.AutoValue_ServerProducerGraph_Input;
import kvprog.bserver.DaggerServerProducerGraph;

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
    return new AutoValue_ServerProducerGraph_Input.Builder().setGetRequest(
            GetRequest.getDefaultInstance())
        .setPutRequest(PutRequest.getDefaultInstance());
  }

  ListenableFuture<GetReply> get();

  ListenableFuture<PutReply> put();

  @ProducerModule
  class ServerProducerModule {

    @Produces
    static GetReply get(GetRequest request, HashMap<String, String> cache) {
      GetReply reply;
      if (request.getKey().length() > 64) {
        reply = GetReply.newBuilder().setFailure(GetReply.Status.SYSTEMERR).build();
      } else if (!cache.containsKey(request.getKey())) {
        reply = GetReply.newBuilder().setFailure(GetReply.Status.NOTFOUND).build();
      } else {
        reply = GetReply.newBuilder().setValue(cache.get(request.getKey())).build();
      }
      return reply;
    }

    @Produces
    static PutReply put(PutRequest request, HashMap<String, String> cache) {
      PutReply reply;
      if (request.getKey().length() > 64 || request.getValue().length() > 512) {
        reply = PutReply.newBuilder().setStatus(Status.SYSTEMERR).build();
      } else {
        cache.put(request.getKey(), request.getValue());
        reply = PutReply.newBuilder().setStatus(Status.SUCCESS).build();
      }
      return reply;
    }
  }

  @AutoValue
  abstract class Input {

    abstract HashMap<String, String> cache();

    abstract GetRequest getRequest();

    abstract PutRequest putRequest();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

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
