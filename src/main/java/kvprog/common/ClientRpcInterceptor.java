package kvprog.common;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.*;
import kvprog.common.InterceptorModule.TraceIdKey;
import kvprog.common.InterceptorModule.CriticalPaths;
import kvprog.common.InterceptorModule.CostListKey;
import kvprog.CostList;
import javax.inject.Inject;
import java.time.Duration;
import java.util.Map;

public class ClientRpcInterceptor implements ClientInterceptor {
  private final Cache<Integer, Long> parallelRpcMonitor;
  private final Map<Integer, CriticalPath> criticalPaths;
  private final Metadata.Key<byte[]> costListKey;
  private final Metadata.Key<String> traceIdMetadataKey;
  private final Ticker ticker;

  @Inject
  ClientRpcInterceptor(
      Cache<Integer, Long> parallelRpcMonitor,
      @CriticalPaths Map<Integer, CriticalPath> criticalPaths,
      @CostListKey Metadata.Key<byte[]> costListKey,
      @TraceIdKey Metadata.Key<String> traceIdMetadataKey,
      Ticker ticker) {
    this.parallelRpcMonitor = parallelRpcMonitor;
    this.criticalPaths = criticalPaths;
    this.costListKey = costListKey;
    this.traceIdMetadataKey = traceIdMetadataKey;
    this.ticker = ticker;
  }

  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> methodDescriptor,
      final CallOptions callOptions,
      final Channel channel) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        channel.newCall(methodDescriptor, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata requestHeader) {
        long startNanos = ticker.read();
        CriticalPathLedger ledger = ProducerRpcContext.getActiveRpcContext().criticalPathLedgerSupplier().currentLedger();
        // Propagate the traceId to downstream from current context.
        Integer traceId = Constants.TRACE_ID_CTX_KEY.get();
        requestHeader.put(traceIdMetadataKey, Integer.toString(traceId));
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener) {
          @Override
          public void onHeaders(Metadata responseHeader) {
            super.onHeaders(responseHeader);
            long endNanos = ticker.read();
            updateCriticalPath(ledger, traceId, startNanos, endNanos, responseHeader);
          }
        }, requestHeader);
      }
    };
  }

  private void updateCriticalPath(
      CriticalPathLedger ledger, Integer traceId, long startNanos, long endNanos, Metadata responseHeader) {
    ledger.recordRpcNode();
    Duration serverTime = Duration.ofNanos(endNanos - startNanos);
    if (!serverTime.isZero()) {
      Long priorEndTimeNanos = parallelRpcMonitor.getIfPresent(traceId);
      if (priorEndTimeNanos != null && startNanos < priorEndTimeNanos) {
        ledger.addParallelRemoteRpcTimeCriticalPath(serverTime);
      } else {
        ledger.addRemoteRpcTimeCriticalPath(serverTime);
      }

      if (priorEndTimeNanos == null || priorEndTimeNanos < endNanos) {
        parallelRpcMonitor.put(traceId, endNanos);
      }
    }

    try {
      CostList cl = CostList.parseFrom(responseHeader.get(costListKey));
      ledger.addRemoteCriticalPath(cl);
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }
  }
}