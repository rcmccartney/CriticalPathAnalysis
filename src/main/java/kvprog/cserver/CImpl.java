package kvprog.cserver;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import kvprog.*;
import kvprog.CGrpc.CImplBase;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;

@GrpcService(grpcClass = CGrpc.class)
public class CImpl extends CImplBase {

  @Inject
  public CImpl() {
  }

  @Override
  public void c1(C1Request req, StreamObserver<C1Reply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("C1")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setC1Request(req).build();
      try {
        responseObserver.onNext(producers.c1().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
  }

  @Override
  public void c2(C2Request req, StreamObserver<C2Reply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("C2")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setC2Request(req).build();
      try {
        responseObserver.onNext(producers.c2().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
  }
}
