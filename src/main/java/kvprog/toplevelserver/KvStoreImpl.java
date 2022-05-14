package kvprog.toplevelserver;

import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

import io.perfmark.traceviewer.TraceEventViewer;
import kvprog.CallInfo;
import kvprog.CallsReply;
import kvprog.CallsRequest;
import kvprog.GetReply;
import kvprog.GetRequest;
import kvprog.KvStoreGrpc;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.PutReply;
import kvprog.PutRequest;
import kvprog.common.InterceptorModule.CallMetadata;
import kvprog.toplevelserver.TopComponentModule.Cache;

@GrpcService(grpcClass = KvStoreGrpc.class)
class KvStoreImpl extends KvStoreImplBase {

  private final Multiset<String> calls;
  private final HashMap<String, String> cache;

  @Inject
  KvStoreImpl(@CallMetadata Multiset<String> calls, @Cache HashMap<String, String> cache) {
    this.calls = calls;
    this.cache = cache;
  }

  @Override
  public void put(PutRequest req, StreamObserver<PutReply> responseObserver) {
    try (TaskCloseable task = PerfMark.traceTask("Put")) {
      ServerProducerGraph producers = ServerProducerGraph
          .builder().setPutRequest(req).setCache(cache).build();
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
          .builder().setGetRequest(req).setCache(cache).build();
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
    try (TaskCloseable task = PerfMark.traceTask("Calls")) {
      CallsReply.Builder reply = CallsReply.newBuilder();
      for (Entry<String> callAndCount : calls.entrySet()) {
        reply.addCallInfo(CallInfo.newBuilder().setCallType(callAndCount.getElement())
            .setCount(callAndCount.getCount()));
      }

      responseObserver.onNext(reply.build());
      responseObserver.onCompleted();
    }
  }
}
