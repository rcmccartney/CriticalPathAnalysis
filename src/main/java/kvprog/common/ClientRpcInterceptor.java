package kvprog.common;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import io.grpc.*;
import kvprog.common.InterceptorModule.TraceIdKey;

import javax.inject.Inject;
import java.time.Duration;

public class ClientRpcInterceptor implements ClientInterceptor {
  private final Cache<Integer, Long> parallelRpcMonitor;
  private final Metadata.Key<String> traceIdMetadataKey;
  private final Ticker ticker;

  @Inject
  ClientRpcInterceptor(
      Cache<Integer, Long> parallelRpcMonitor,
      @TraceIdKey Metadata.Key<String> traceIdMetadataKey,
      Ticker ticker) {
    this.parallelRpcMonitor = parallelRpcMonitor;
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
        ProducerRpcContext context = ProducerRpcContext.getActiveRpcContext();
        CriticalPathLedger ledger = context.criticalPathLedgerSupplier().currentLedger();
        // Propagate the traceId to downstream from current context.
        Integer traceId = Constants.TRACE_ID_CTX_KEY.get();
        System.err.println("MY CURRENT TRACEID: " + traceId);
        requestHeader.put(traceIdMetadataKey, Integer.toString(traceId));
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
            responseListener) {
          @Override
          public void onHeaders(Metadata responseHeader) {
            super.onHeaders(responseHeader);
            long endNanos = ticker.read();
            updateCriticalPath(ledger, traceId, startNanos, endNanos);
          }
        }, requestHeader);
      }
    };
  }

  private void updateCriticalPath(CriticalPathLedger ledger, Integer traceId, long startNanos, long endNanos) {
    ledger.recordRpcNode();
    long serverTimeSec = Duration.ofNanos(endNanos - startNanos).toSeconds();
    if (serverTimeSec > 0) {
      Long priorEndTimeNanos = parallelRpcMonitor.getIfPresent(traceId);
      if (priorEndTimeNanos != null && startNanos < priorEndTimeNanos) {
        ledger.addParallelRemoteRpcTimeCriticalPath(serverTimeSec);
      } else {
        ledger.addRemoteRpcTimeCriticalPath(serverTimeSec);
      }

      if (priorEndTimeNanos == null || priorEndTimeNanos < endNanos) {
        parallelRpcMonitor.put(traceId, endNanos);
      }
    }

//    CriticalPathExtension criticalPathExtension =
//        ctx.getResponseExtensions()
//            .get(
//                CriticalPathExtension.MESSAGE_SET_EXTENSION_FIELD_NUMBER,
//                CriticalPathExtension.getDefaultInstance());
//    if (criticalPathExtension != null && criticalPathExtension.hasCostList()) {
//      ledger.addRemoteCriticalPath(criticalPathExtension.getCostList());
//    }
  }
}