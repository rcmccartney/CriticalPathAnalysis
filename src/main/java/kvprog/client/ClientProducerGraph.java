package kvprog.client;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.multibindings.ElementsIntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import kvprog.*;
import kvprog.common.ExecutorModule;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

@Singleton
@ProductionComponent(
    modules = {
        ClientProducerGraph.ClientProducerModule.class,
        ExecutorModule.class,
    },
    dependencies = ClientProducerGraph.Input.class)
interface ClientProducerGraph {

  /**
   * Static factory method for {@link Input.Builder}
   */
  static Input.Builder builder() {
    return new AutoValue_ClientProducerGraph_Input.Builder().setCount(1);
  }

  @ClientProducerModule.Get
  ListenableFuture<String> sendGet();

  @ClientProducerModule.Put
  ListenableFuture<String> sendPut();

  @ProducerModule
  class ClientProducerModule {

    @ElementsIntoSet
    @Produces
    static Set<ListenableFuture<GetReply>> get(Input input) {
      return IntStream.range(0, input.count())
          .boxed()
          .map(i -> GetRequest.newBuilder().setKey(input.key().get()).build())
          .map(request -> input.stub().get(request))
          .collect(ImmutableSet.toImmutableSet());
    }

    @Produces
    @Get
    static String sendGet(Set<GetReply> reply) {
      Set<String> vals = reply.stream().map(ClientProducerModule::convertGetReplyToString)
          .collect(ImmutableSet.toImmutableSet());
      return Joiner.on("\n").join(vals);
    }

    static String convertGetReplyToString(GetReply reply) {
      if (reply.hasValue()) {
        return "Response: " + reply.getValue();
      } else {
        return "Lookup failed: " + reply.getFailure();
      }
    }

    @ElementsIntoSet
    @Produces
    static Set<ListenableFuture<PutReply>> put(Input input) {
      return IntStream.range(0, input.count())
          .boxed()
          .map(i -> PutRequest.newBuilder().setKey(input.key().get()).setValue(input.value().get())
              .build())
          .map(request -> input.stub().put(request))
          .collect(ImmutableSet.toImmutableSet());
    }

    @Produces
    @Put
    static String sendPut(Set<PutReply> reply) {
      Set<String> vals = reply.stream().map(response -> "Response: " + response.getStatus())
          .collect(ImmutableSet.toImmutableSet());
      return Joiner.on("\n").join(vals);
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
  }

  @AutoValue
  abstract class Input {

    abstract KvStoreGrpc.KvStoreFutureStub stub();

    abstract Optional<String> key();

    abstract Optional<String> value();

    abstract int count();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Input autoBuild();

      abstract Builder setStub(KvStoreGrpc.KvStoreFutureStub stub);

      abstract Builder setKey(String key);

      abstract Builder setValue(String value);

      abstract Builder setCount(int value);

      /**
       * Build the {@link ClientProducerGraph}
       */
      final ClientProducerGraph build() {
        return DaggerClientProducerGraph.builder()
            .input(autoBuild())
            .build();
      }
    }
  }
}
