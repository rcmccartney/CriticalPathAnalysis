package kvprog.server;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import kvprog.*;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.PutReply.Status;

import javax.inject.Inject;
import java.util.HashMap;

@GrpcService(grpcClass = KvStoreGrpc.class)
class KvStoreImpl extends KvStoreImplBase {

  private final HashMap<String, String> cache;

  @Inject
  KvStoreImpl(@TopComponentModule.Cache HashMap<String, String> cache) {
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
}
