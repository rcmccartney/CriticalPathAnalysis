package kvprog.toplevelserver;

import com.google.common.collect.Multiset;
import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import io.perfmark.traceviewer.TraceEventViewer;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

import kvprog.*;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.common.InterceptorModule.CallMetadata;
import kvprog.toplevelserver.TopComponentModule.Cache;

@GrpcService(grpcClass = KvStoreGrpc.class)
class KvStoreImpl extends KvStoreImplBase {

  private final Multiset<String> calls;
  private final HashMap<String, String> cache;
  private final BGrpc.BFutureStub bstub;
  private final CGrpc.CFutureStub cstub;

  @Inject
  KvStoreImpl(
      @CallMetadata Multiset<String> calls,
      @Cache HashMap<String, String> cache,
      BGrpc.BFutureStub bstub,
      CGrpc.CFutureStub cstub) {
    this.calls = calls;
    this.cache = cache;
    this.bstub = bstub;
    this.cstub = cstub;
  }

  @Override
  public void put(PutRequest req, StreamObserver<PutReply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("Put")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setBStub(bstub).setCStub(cstub).setPutRequest(req)
          .setGetRequest(GetRequest.newBuilder().setKey(req.getKey()).build()).setCache(cache)
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
  }

  @Override
  public void get(GetRequest req, StreamObserver<GetReply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("Get")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setBStub(bstub).setCStub(cstub).setGetRequest(req).setCache(cache).build();
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
  }

  @Override
  public void calls(CallsRequest req, StreamObserver<CallsReply> responseObserver) {
    CallsReply.Builder reply = CallsReply.newBuilder();
    reply
        // Request 1 from client.
        .addCostList(CostList.newBuilder()
            .addElement(CostElement.newBuilder().setCostSec(10).setSource("Put_Node1"))
            .addElement(CostElement.newBuilder().setCostSec(4).setSource("Put_Node2"))
            .addElement(CostElement.newBuilder().setCostSec(1).setSource("Put_Node3"))
            .addElement(CostElement.newBuilder().setCostSec(8).setSource("Put_Node4")))
        .addCostList(CostList.newBuilder()
            .addElement(CostElement.newBuilder().setCostSec(10).setSource("Get_Node1"))
            .addElement(CostElement.newBuilder().setCostSec(4).setSource("Get_Node4")))
        .addCostList(CostList.newBuilder()
            .addElement(CostElement.newBuilder().setCostSec(10).setSource("Put_Node1"))
            .addElement(CostElement.newBuilder().setCostSec(1).setSource("Put_Node3"))
            .addElement(CostElement.newBuilder().setCostSec(8).setSource("Put_Node2")));

    responseObserver.onNext(reply.build());
    responseObserver.onCompleted();
  }
}
