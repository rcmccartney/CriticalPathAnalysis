package kvprog.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.util.HashMap;
import kvprog.GetReply;
import kvprog.GetRequest;
import kvprog.KvStoreGrpc;
import kvprog.KvStoreGrpc.KvStoreImplBase;
import kvprog.PutReply;
import kvprog.PutReply.Status;
import kvprog.PutRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/**
 * Unit tests for {@link KvProgClient}.
 *
 * <p>Note: directExecutor() makes it easier to have deterministic tests.
 * However, if your implementation uses another thread and uses streaming it is better to use the
 * default executor, to avoid hitting bug #3084.
 */
@RunWith(JUnit4.class)
public class KvProgClientTest {

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

  private KvProgClient client;

  @Before
  public void setUp() throws Exception {
    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(InProcessServerBuilder
        .forName(serverName).directExecutor().addService(serviceImpl).build().start());

    // Create a client channel and register for automatic graceful shutdown.
    ManagedChannel channel = grpcCleanup.register(
        InProcessChannelBuilder.forName(serverName).directExecutor().build());

    // Create a HelloWorldClient using the in-process channel;
    client = new KvProgClient(KvStoreGrpc.newBlockingStub(channel));
  }

  @Test
  public void get_reachesService() {
    ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);

    client.get("missing key");

    verify(serviceImpl)
        .get(requestCaptor.capture(), ArgumentMatchers.any());
    assertEquals("missing key", requestCaptor.getValue().getKey());
  }

  @Test
  public void put_reachesService() {
    ArgumentCaptor<PutRequest> requestCaptor = ArgumentCaptor.forClass(PutRequest.class);

    client.put("100", "100");

    verify(serviceImpl)
        .put(requestCaptor.capture(), ArgumentMatchers.any());
    assertEquals("100", requestCaptor.getValue().getKey());
  }
}