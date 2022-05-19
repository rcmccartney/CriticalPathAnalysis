package kvprog.bserver;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import kvprog.*;
import kvprog.BGrpc.BImplBase;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;

@GrpcService(grpcClass = BGrpc.class)
class BImpl extends BImplBase {

  @Inject
  BImpl() {
  }

  @Override
  public void b1(B1Request req, StreamObserver<B1Reply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("B1")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setB1Request(req).build();
      try {
        responseObserver.onNext(producers.b1().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
  }

  @Override
  public void b2(B2Request req, StreamObserver<B2Reply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("B2")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setB2Request(req).build();
      try {
        responseObserver.onNext(producers.b2().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
  }
}
