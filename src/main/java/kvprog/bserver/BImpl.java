package kvprog.bserver;

import dagger.grpc.server.GrpcService;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import kvprog.B1Reply;
import kvprog.B1Request;
import kvprog.B2Reply;
import kvprog.B2Request;
import kvprog.BGrpc;
import kvprog.BGrpc.BImplBase;

@GrpcService(grpcClass = BGrpc.class)
class BImpl extends BImplBase {

  @Inject
  BImpl() {
  }

  @Override
  public void b1(B1Request req, StreamObserver<B1Reply> responseObserver) {
  }

  @Override
  public void b2(B2Request req, StreamObserver<B2Reply> responseObserver) {
  }
}
