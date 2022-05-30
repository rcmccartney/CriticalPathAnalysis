package kvprog.cserver;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import kvprog.C1Reply;
import kvprog.C1Request;
import kvprog.C2Reply;
import kvprog.C2Request;
import kvprog.CGrpc;
import kvprog.CGrpc.CImplBase;
import kvprog.common.CriticalPath;
import kvprog.common.InterceptorModule.CriticalPaths;

@GrpcService(grpcClass = CGrpc.class)
public class CImpl extends CImplBase {

  private final Map<Integer, CriticalPath> criticalPaths;

  @Inject
  public CImpl(@CriticalPaths Map<Integer, CriticalPath> criticalPaths) {
    this.criticalPaths = criticalPaths;
  }

  @Override
  public void c1(C1Request req, StreamObserver<C1Reply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("C1")) {
      CsProducerGraph producers = CsProducerGraph
          .builder()
          .setCriticalPaths(criticalPaths)
          .setC1Request(req)
          .build();
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
      CsProducerGraph producers = CsProducerGraph
          .builder()
          .setCriticalPaths(criticalPaths)
          .setC1Request(C1Request.getDefaultInstance())
          .setC2Request(req)
          .build();
      try {
        responseObserver.onNext(producers.c2().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
  }
}
