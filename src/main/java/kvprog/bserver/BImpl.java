package kvprog.bserver;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import kvprog.B1Reply;
import kvprog.B1Request;
import kvprog.B2Reply;
import kvprog.B2Request;
import kvprog.BGrpc;
import kvprog.BGrpc.BImplBase;
import kvprog.CGrpc;
import kvprog.common.InternalCriticalPath;
import kvprog.common.InterceptorModule.CriticalPaths;

@GrpcService(grpcClass = BGrpc.class)
public class BImpl extends BImplBase {

  private final CGrpc.CFutureStub stub;
  private final Map<Integer, InternalCriticalPath> criticalPaths;

  @Inject
  public BImpl(CGrpc.CFutureStub stub, @CriticalPaths Map<Integer, InternalCriticalPath> criticalPaths) {
    this.stub = stub;
    this.criticalPaths = criticalPaths;
  }

  @Override
  public void b1(B1Request req, StreamObserver<B1Reply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("B1")) {
      BsProducerGraph producers = BsProducerGraph
          .builder()
          .setCStub(stub)
          .setCriticalPaths(criticalPaths)
          .setB1Request(req)
          .setB2Request(B2Request.newBuilder().setCallC(false).build())
          .build();
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
      BsProducerGraph producers = BsProducerGraph
          .builder()
          .setCriticalPaths(criticalPaths)
          .setCStub(stub)
          .setB2Request(req)
          .build();
      try {
        responseObserver.onNext(producers.b2().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
  }
}
