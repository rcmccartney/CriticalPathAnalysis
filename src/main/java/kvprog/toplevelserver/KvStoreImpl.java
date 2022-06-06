package kvprog.toplevelserver;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import io.perfmark.traceviewer.TraceEventViewer;
import kvprog.*;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.common.InternalCriticalPath;
import kvprog.common.InterceptorModule.CriticalPaths;
import kvprog.toplevelserver.TopComponentModule.Cache;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@GrpcService(grpcClass = KvStoreGrpc.class)
class KvStoreImpl extends KvStoreImplBase {

  private final Map<String, String> cache;
  private final Map<Integer, InternalCriticalPath> criticalPaths;
  private final BGrpc.BFutureStub bstub;
  private final CGrpc.CFutureStub cstub;

  @Inject
  KvStoreImpl(
      @Cache Map<String, String> cache,
      @CriticalPaths Map<Integer, InternalCriticalPath> criticalPaths,
      BGrpc.BFutureStub bstub,
      CGrpc.CFutureStub cstub) {
    this.cache = cache;
    this.criticalPaths = criticalPaths;
    this.bstub = bstub;
    this.cstub = cstub;
  }

  @Override
  public void put(PutRequest req, StreamObserver<PutReply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("Put")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder()
          .setBStub(bstub)
          .setCStub(cstub)
          .setPutRequest(req)
          .setGetRequest(GetRequest.newBuilder().setKey(req.getKey()).build())
          .setCache(cache)
          .setCriticalPaths(criticalPaths)
          .build();
      try {
        responseObserver.onNext(producers.put().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
    try {
      TraceEventViewer.writeTraceHtml();
    } catch (IOException e) {
      e.printStackTrace();
    }

    Optional<Integer> lastSpan = criticalPaths.keySet().stream().max(Integer::compareTo);
    lastSpan.ifPresent(spanId -> System.err.println("Cost list of PutRequest: " + criticalPaths.get(spanId).toCriticalPath()));
  }

  @Override
  public void get(GetRequest req, StreamObserver<GetReply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("Get")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder()
          .setBStub(bstub)
          .setCStub(cstub)
          .setGetRequest(req)
          .setCache(cache)
          .setCriticalPaths(criticalPaths)
          .build();
      try {
        responseObserver.onNext(producers.get().get());
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      responseObserver.onCompleted();
    }
    try {
      TraceEventViewer.writeTraceHtml();
    } catch (IOException e) {
      e.printStackTrace();
    }

    Optional<Integer> lastSpan = criticalPaths.keySet().stream().max(Integer::compareTo);
    lastSpan.ifPresent(spanId -> System.err.println("Cost list of GetRequest: " + criticalPaths.get(spanId).toCriticalPath()));
  }

  @Override
  public void calls(CallsRequest req, StreamObserver<CallsReply> responseObserver) {
    CallsReply.Builder reply = CallsReply.newBuilder();
    criticalPaths.values().stream().map(InternalCriticalPath::toCriticalPath)
            .forEach(reply::addCriticalPath);
    responseObserver.onNext(reply.build());
    responseObserver.onCompleted();
  }
}
