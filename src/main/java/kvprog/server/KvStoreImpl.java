package kvprog.server;

import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import javax.inject.Inject;
import kvprog.CallInfo;
import kvprog.CallsReply;
import kvprog.CallsRequest;
import kvprog.GetReply;
import kvprog.GetRequest;
import kvprog.KvStoreGrpc;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.PutReply;
import kvprog.PutReply.Status;
import kvprog.PutRequest;
import kvprog.server.TopComponentModule.Cache;
import kvprog.server.TopComponentModule.CallData;

@GrpcService(grpcClass = KvStoreGrpc.class)
class KvStoreImpl extends KvStoreImplBase {

  private final Multiset<String> calls;
  private final HashMap<String, String> cache;

  @Inject
  KvStoreImpl(@CallData Multiset<String> calls, @Cache HashMap<String, String> cache) {
    this.calls = calls;
    this.cache = cache;
  }

  @Override
  public void put(PutRequest req, StreamObserver<PutReply> responseObserver) {
    PutReply reply;
    if (req.getKey().length() > 64 || req.getValue().length() > 512) {
      reply = PutReply.newBuilder().setStatus(Status.SYSTEMERR).build();
    } else {
      cache.put(req.getKey(), req.getValue());
      reply = PutReply.newBuilder().setStatus(Status.SUCCESS).build();
    }

    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }

  @Override
  public void get(GetRequest req, StreamObserver<GetReply> responseObserver) {
    GetReply reply;
    if (req.getKey().length() > 64) {
      reply = GetReply.newBuilder().setFailure(GetReply.Status.SYSTEMERR).build();
    } else if (!cache.containsKey(req.getKey())) {
      reply = GetReply.newBuilder().setFailure(GetReply.Status.NOTFOUND).build();
    } else {
      reply = GetReply.newBuilder().setValue(cache.get(req.getKey())).build();
    }

    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }

  @Override
  public void calls(CallsRequest req, StreamObserver<CallsReply> responseObserver) {
    CallsReply.Builder reply = CallsReply.newBuilder();

    for (Entry<String> callAndCount : calls.entrySet()) {
      reply.addCallInfo(CallInfo.newBuilder().setCallType(callAndCount.getElement())
          .setCount(callAndCount.getCount()));
    }

    responseObserver.onNext(reply.build());
    responseObserver.onCompleted();
  }
}
