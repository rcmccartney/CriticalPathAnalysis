package kvprog.client;

import com.google.common.collect.ConcurrentHashMultiset;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import kvprog.*;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.PutReply.Status;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LoadGenerator}.
 *
 * <p>Note: directExecutor() makes it easier to have deterministic tests.
 * However, if your implementation uses another thread and uses streaming it is better to use the
 * default executor, to avoid hitting bug #3084.
 */
@RunWith(JUnit4.class)
public class LoadGeneratorTest {

  /**
   * This rule manages automatic graceful shutdown for the registered servers and channels at the
   * end of test.
   */
  @Rule
  public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private final KvStoreImplBase serviceImpl =
      mock(KvStoreImplBase.class, delegatesTo(
          new KvStoreImplBase() {
            private final HashMap<String, String> cache = new HashMap<>();

            @Override
            public void put(PutRequest req, StreamObserver<PutReply> responseObserver) {
              cache.put(req.getKey(), req.getValue());
              responseObserver.onNext(PutReply.newBuilder().setStatus(Status.SUCCESS).build());
              responseObserver.onCompleted();
            }

            @Override
            public void calls(CallsRequest req, StreamObserver<CallsReply> responseObserver) {
              responseObserver.onNext(CallsReply.getDefaultInstance());
              responseObserver.onCompleted();
            }

            @Override
            public void get(GetRequest req, StreamObserver<GetReply> responseObserver) {
              GetReply reply;
              if (!cache.containsKey(req.getKey())) {
                reply = GetReply.newBuilder().setFailure(GetReply.Status.NOTFOUND).build();
              } else {
                reply = GetReply.newBuilder().setValue(cache.get(req.getKey())).build();
              }

              responseObserver.onNext(reply);
              responseObserver.onCompleted();
            }
          }));

  private LoadGenerator loadGen;

  @Before
  public void setUp() throws Exception {
    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(InProcessServerBuilder
        .forName(serverName).directExecutor().addService(serviceImpl).build().start());

    // Create a client channel and register for automatic graceful shutdown.
    ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

    // Create a client using the in-process channel;
    loadGen = new LoadGenerator(KvStoreGrpc.newFutureStub(channel));
  }

  @Test
  public void get_reachesService() {
    ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
    loadGen.get("missing key");
    verify(serviceImpl, times(1)).get(requestCaptor.capture(), ArgumentMatchers.any());
    assertEquals("missing key", requestCaptor.getValue().getKey());
  }

  @Test
  public void put_reachesService() {
    ArgumentCaptor<PutRequest> requestCaptor = ArgumentCaptor.forClass(PutRequest.class);
    loadGen.put("100", "100");
    verify(serviceImpl, times(1)).put(requestCaptor.capture(), ArgumentMatchers.any());
    assertEquals("100", requestCaptor.getValue().getKey());
  }
}