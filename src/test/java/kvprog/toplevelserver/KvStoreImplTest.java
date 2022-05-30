package kvprog.toplevelserver;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import kvprog.*;
import kvprog.KvStoreGrpc.KvStoreBlockingStub;
import kvprog.PutReply.Status;
import kvprog.bserver.BImpl;
import kvprog.common.CriticalPath;
import kvprog.cserver.CImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link TopLevelServer}.
 *
 * <p>Note: directExecutor() makes it easier to have deterministic tests.
 * However, if your implementation uses another thread and uses streaming it is better to use the
 * default executor, to avoid hitting bug #3084.
 */
@RunWith(JUnit4.class)
public class KvStoreImplTest {

  /**
   * This rule manages automatic graceful shutdown for the registered servers and channels at the
   * end of test.
   */
  @Rule
  public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  /**
   * To test the server, make calls with a real stub using the in-process channel, and verify
   * behaviors or state changes from the client side.
   */
  @Test
  public void serverImpl_replyMessage() throws Exception {
    // Generate a unique in-process server name.
    String topServerName = InProcessServerBuilder.generateName();
    String bServerName = InProcessServerBuilder.generateName();
    String cServerName = InProcessServerBuilder.generateName();
    Multiset<String> calls = ConcurrentHashMultiset.create();
    HashMap<String, String> cache = new HashMap<>();
    HashMap<Integer, CriticalPath> criticalPaths = new HashMap<>();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(InProcessServerBuilder
        .forName(cServerName).directExecutor().addService(new CImpl(criticalPaths)).build().start());
    ManagedChannel channel = grpcCleanup.register(
        InProcessChannelBuilder.forName(cServerName).directExecutor().build());
    CGrpc.CFutureStub cStub = CGrpc.newFutureStub(channel);
    grpcCleanup.register(InProcessServerBuilder
        .forName(bServerName).directExecutor().addService(new BImpl(cStub, criticalPaths)).build().start());
    channel = grpcCleanup.register(
        InProcessChannelBuilder.forName(bServerName).directExecutor().build());
    BGrpc.BFutureStub bStub = BGrpc.newFutureStub(channel);
    grpcCleanup.register(InProcessServerBuilder
        .forName(topServerName).directExecutor()
        .addService(new KvStoreImpl(cache, criticalPaths, bStub, cStub)).build().start());

    KvStoreBlockingStub blockingStub = KvStoreGrpc.newBlockingStub(
        // Create a client channel and register for automatic graceful shutdown.
        grpcCleanup.register(
            InProcessChannelBuilder.forName(topServerName).directExecutor().build()));

    GetReply getReply =
        blockingStub.get(GetRequest.newBuilder().setKey("missing key").build());
    assertEquals("", getReply.getValue());

    PutReply putReply =
        blockingStub.put(PutRequest.newBuilder().setKey("100").setValue("100").build());
    assertEquals(Status.SUCCESS, putReply.getStatus());
    getReply =
        blockingStub.get(GetRequest.newBuilder().setKey("100").build());
    assertEquals("100", getReply.getValue());

    // The interceptor isn't registered for the InProcessServer.
    assertEquals(0, calls.size());
  }
}
