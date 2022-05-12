package kvprog.client;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import kvprog.*;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;

@Singleton
@ProductionComponent(
    modules = {
        ClientProductionComponent.ClientProducerModule.class,
        ExecutorModule.class,
    },
    dependencies = ClientProductionComponent.Input.class)
interface ClientProductionComponent {

  @ClientProducerModule.Get ListenableFuture<String> sendGet();

  @ClientProducerModule.Put ListenableFuture<String> sendPut();

  @ClientProducerModule.CallData ListenableFuture<String> callData();

  @ProducerModule
  class ClientProducerModule {

    @Produces
    static ListenableFuture<GetReply> get(Input input) {
      GetRequest request = GetRequest.newBuilder().setKey(input.key().get()).build();
      return input.stub().get(request);
    }

    @Produces
    @Get
    static String sendGet(GetReply reply) {
      if (reply.hasValue()) {
        return "Response: " + reply.getValue();
      } else {
        return "Lookup failed: " + reply.getFailure();
      }
    }

    @Produces
    static ListenableFuture<PutReply> put(Input input) {
      PutRequest request = PutRequest.newBuilder().setKey(input.key().get()).setValue(input.value().get()).build();
      return input.stub().put(request);
    }

    @Produces
    @Put
    static String sendPut(PutReply response) {
      return "Response: " + response.getStatus();
    }

    @Produces
    static ListenableFuture<CallsReply> calls(Input input) {
      return input.stub().calls(CallsRequest.getDefaultInstance());
    }

    @Produces
    @CallData
    static String sendCalls(CallsReply response) {
      String result;
      result = "** Call Type : Count **\n";
      for (CallInfo info : response.getCallInfoList()) {
        result += String.format("%s : %s\n", info.getCallType(), info.getCount());
      }
      return result;
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

    @Qualifier
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @interface CallData {
    }
  }

  @AutoValue
  abstract class Input {
    static Builder newBuilder() {
      return new AutoValue_ClientProductionComponent_Input.Builder();
    }

    abstract KvStoreGrpc.KvStoreFutureStub stub();

    abstract Optional<String> key();

    abstract Optional<String> value();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setStub(KvStoreGrpc.KvStoreFutureStub stub);

      abstract Builder setKey(String key);

      abstract Builder setValue(String value);

      abstract Input build();
    }
  }
}
