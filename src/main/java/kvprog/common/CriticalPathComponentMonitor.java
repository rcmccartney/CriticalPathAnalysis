package kvprog.common;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import dagger.grpc.server.CallScoped;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import kvprog.CostList;

/**
 * A monitor that calculates the critical path of execution.
 */
public final class CriticalPathComponentMonitor extends ProductionComponentMonitor {

  // Records the producer that's currently running on the current thread.
  private static final ThreadLocal<ProducerToken> activeProducer = new ThreadLocal<>();
  private final String componentName;
  private final Ticker ticker;
  private final Map<ProducerToken, CriticalPathProducerMonitor> producerMonitors = new HashMap<>();

  CriticalPathComponentMonitor(String componentName, Ticker ticker) {
    this.componentName = componentName;
    this.ticker = ticker;
  }

  @Override
  public synchronized ProducerMonitor producerMonitorFor(ProducerToken token) {
    CriticalPathProducerMonitor monitor = new CriticalPathProducerMonitor(token);
    producerMonitors.put(token, monitor);
    return monitor;
  }

  synchronized long getTotalCpuUsec() {
    long cpuUsec = 0;
    for (CriticalPathProducerMonitor recorder : producerMonitors.values()) {
      cpuUsec += recorder.cpuUsec();
    }
    return cpuUsec;
  }

  /**
   * The factory that creates {@code StatsMonitor}. This is scoped because it collects data
   * throughout the request on behalf of each production component that's run.
   */
  @CallScoped
  public static final class Factory extends ProductionComponentMonitor.Factory {

    private final ChildCostLists childCostLists;
    private final Map<String, CriticalPathComponentMonitor> statsMonitors = new LinkedHashMap<>();
    private final ProductionExecutionComponentMonitor.Factory productionExecutionMonitorFactory;
    private final Names componentNames;
    private final Ticker ticker;

    @Inject
    Factory(
        ChildCostLists childCostLists,
        ProductionExecutionComponentMonitor.Factory productionExecutionMonitorFactory,
        Names componentNames) {
      this.childCostLists = childCostLists;
      this.productionExecutionMonitorFactory = productionExecutionMonitorFactory;
      this.componentNames = componentNames;
      this.ticker = Ticker.systemTicker();
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      CriticalPathComponentMonitor componentMonitor =
          new CriticalPathComponentMonitor(componentNames.getName(component), ticker);
      statsMonitors.put(componentMonitor.componentName, componentMonitor);
      return componentMonitor;
    }

    @Nullable
    private CriticalPathProducerMonitor getProducerMonitor(GraphProducerToken graphProducerToken) {
      CriticalPathComponentMonitor componentMonitor = statsMonitors.get(
          graphProducerToken.graphName());
      if (componentMonitor == null) {
        System.err.println("ComponentMonitor is null!");
        return null;
      }
      return componentMonitor.producerMonitors.get(graphProducerToken.producerToken());
    }

    public synchronized long totalCpuUsec() {
      long cpuUsec = 0;
      for (CriticalPathComponentMonitor monitor : statsMonitors.values()) {
        cpuUsec += monitor.getTotalCpuUsec();
      }
      return cpuUsec;
    }

    /**
     * Returns the critical path in the producer graph.
     *
     * <p>The critical path is computed using the following (heuristic) algorithm:
     *
     * <ol>
     *   <li>Find the sink producer. The sink producer is the most recently started node.
     *   <li>For a producer P on the critical path, determine if P was waiting on the request thread
     *       to execute a previously scheduled producer or if P was waiting on an RPC to return.
     * </ol>
     */
    public synchronized CriticalPath criticalPath() {
      if (statsMonitors.isEmpty()) {
        return CriticalPath.empty();
      }
      return singleThreadedCriticalPath(childCostLists);
    }

    /**
     * This algorithm is simplified by assuming execution is on a single request thread.
     */
    private CriticalPath singleThreadedCriticalPath(ChildCostLists childCostLists) {
      ImmutableList<GraphProducerToken> executionOrder =
          productionExecutionMonitorFactory.getExecutionOrder();
      if (executionOrder.isEmpty()) {
        return CriticalPath.create(
            CriticalPath.Node.create(
                "", /* graph name */
                0, /* start time */
                0, /* end time */
                CriticalPath.builder().build()));
      }

      int index = executionOrder.size() - 1;
      // There is no useful information from unstarted nodes, so we skip them.
      while (index > 0 && getProducerMonitor(executionOrder.get(index)).endTimeUsec() == 0) {
        index--;
      }

      ImmutableList<GraphProducerToken> rpcCompletionOrder =
          getRpcNodesByEndTime(executionOrder.subList(0, index), childCostLists);
      GraphProducerToken sink = executionOrder.get(index);

      return CriticalPath.create(
          CriticalPath.Node.create(
              sink.graphName(), // TODO(kas): Why sink.graphName()?
              0,
              getProducerMonitor(sink).endTimeUsec(),
              buildCriticalPathFromSink(
                  sink, index, rpcCompletionOrder, executionOrder, childCostLists)
                  .build()));
    }

    private CriticalPath.Builder buildCriticalPathFromSink(
        GraphProducerToken sink,
        int index,
        ImmutableList<GraphProducerToken> rpcCompletionOrder,
        ImmutableList<GraphProducerToken> executionOrder,
        ChildCostLists childCostLists) {
      CriticalPath.Builder builder = CriticalPath.builder();
      GraphProducerToken currentCriticalProducer = sink;
      List<GraphProducerToken> criticalPath = new ArrayList<>();
      criticalPath.add(currentCriticalProducer);

      int rpcIndex = rpcCompletionOrder.size() - 1;
      // Compute critical path.
      long criticalPathStartTimeUsec = getProducerMonitor(sink).startTimeUsec();
      while (index > 0) {
        currentCriticalProducer = executionOrder.get(--index);
        CriticalPathProducerMonitor pr = getProducerMonitor(currentCriticalProducer);

        // Choose between the previously executed node and the RPC
        // node whose end time is nearest, but prior to, the current
        // critical path start time.
        long cpuSlack = criticalPathStartTimeUsec - (pr.startTimeUsec() + pr.cpuUsec());
        long rpcSlack = Long.MAX_VALUE;
        GraphProducerToken rpcToken = null;
        while (rpcIndex >= 0) {
          rpcToken = rpcCompletionOrder.get(rpcIndex);
          CriticalPathProducerMonitor newPr = getProducerMonitor(rpcToken);
          if (newPr.endTimeUsec() <= criticalPathStartTimeUsec) {
            rpcSlack = criticalPathStartTimeUsec - newPr.endTimeUsec();
            break;
          }
          rpcIndex--;
        }
        if (rpcSlack < cpuSlack) {
          // Walk the executionOrder list to find the chosen RPC node.
          while (!rpcToken.equals(executionOrder.get(index))) {
            index--;
          }
          currentCriticalProducer = executionOrder.get(index);
          pr = getProducerMonitor(currentCriticalProducer);
        }
        criticalPathStartTimeUsec = pr.startTimeUsec();
        criticalPath.add(currentCriticalProducer);
      }

      // Compute latency of each node in the critical path. The
      // latency of a node is the difference between its start time
      // and the smaller of its end time or the start time of its
      // child in the path. If there is a gap between the end time of
      // the current node and the start time of the next node, we add
      // that to a special framework latency symbol.
      long frameworkLatencyUsec = 0;
      CriticalPathProducerMonitor currentRecorder = null;
      CriticalPathProducerMonitor previousRecorder = null;
      ImmutableListMultimap<GraphProducerToken, CostList> tokenToCostList =
          childCostLists.costLists();
      for (GraphProducerToken token : criticalPath) {
        long latencyUsec;
        currentRecorder = getProducerMonitor(token);
        ImmutableList<CostList> costLists =
            tokenToCostList.entries().stream()
                .filter(map -> map.getKey().producerToken().equals(token.producerToken()))
                .map(Map.Entry::getValue)
                .collect(ImmutableList.toImmutableList());

        if (previousRecorder == null) {
          latencyUsec = currentRecorder.latencyUsec();
        } else {
          long endTimeUsec =
              Math.min(previousRecorder.startTimeUsec(), currentRecorder.endTimeUsec());
          latencyUsec = Math.max(0, endTimeUsec - currentRecorder.startTimeUsec());
          // If current node is a RPC node and its end time is later than the start time of
          // previous(backward) node, then this RPC node is picked on CPU finish time basis.
          boolean isRpcNodePickedByCpuSlack =
              childCostLists.isRpcNode(token)
                  && currentRecorder.endTimeUsec() > previousRecorder.startTimeUsec();
          if (isRpcNodePickedByCpuSlack) {
            // Introduce a child element to account for a portion of current rpc node's RPC time.
            // It's a gap between current rpc node's CPU finish time and the start time of the
            // previous(backward) node and it will replace the rpc node's original child elements.
            long rpcGapUsec =
                previousRecorder.startTimeUsec()
                    - (currentRecorder.startTimeUsec() + currentRecorder.cpuUsec());
            costLists =
                rpcGapUsec > 0
                    ? childCostListsFromLatencyUsec("rpc-gap", rpcGapUsec)
                    : ImmutableList.of();
          }
          frameworkLatencyUsec += previousRecorder.startTimeUsec() - endTimeUsec;
        }
        builder.addNode(criticalPathNodeFromProducer(costLists, token, latencyUsec));
        previousRecorder = currentRecorder;
      }
      if (frameworkLatencyUsec > 0) {
        builder.addNode(
            CriticalPath.Node.create(
                "<framework>", frameworkLatencyUsec, frameworkLatencyUsec, ImmutableList.of()));
      }

      return builder;
    }

    private ImmutableList<GraphProducerToken> getRpcNodesByEndTime(
        ImmutableList<GraphProducerToken> executionOrder, ChildCostLists childCostLists) {
      return executionOrder.stream()
          .filter(childCostLists::isRpcNode)
          .sorted(
              new Comparator<GraphProducerToken>() {
                @Override
                public int compare(GraphProducerToken a, GraphProducerToken b) {
                  return Long.compare(
                      getProducerMonitor(a).endTimeUsec(), getProducerMonitor(b).endTimeUsec());
                }
              })
          .collect(ImmutableList.toImmutableList());
    }

    private CriticalPath.Node criticalPathNodeFromProducer(
        ImmutableList<CostList> childCostLists, GraphProducerToken producer, long latencyUsec) {
      CriticalPathProducerMonitor producerRecorder = getProducerMonitor(producer);
      return CriticalPath.Node.builder()
          .name(Names.producerName(producer.producerToken()))
          .cpuUsec(Math.min(latencyUsec, producerRecorder.cpuUsec()))
          .latencyUsec(latencyUsec)
          .childCostLists(childCostLists)
          .build();
    }

    private ImmutableList<CostList> childCostListsFromLatencyUsec(
        String nodeName, long lantencyUsec) {
      CostList.Builder builder = CostList.newBuilder();
      builder.addElement(CriticalPath.newCostElement("/" + nodeName, lantencyUsec / 1e6));
      return ImmutableList.of(builder.build());
    }
  }

  final class CriticalPathProducerMonitor extends ProducerMonitor {

    private final ProducerToken producerToken;

    // These variables are volatile so that updates to them on producer threads (e.g., stubby
    // threads) will be read by the main request thread.
    private volatile long readyNanos = -1;
    private volatile long startedNanos = -1;
    private volatile long durationNanos = -1;
    private volatile long completedNanos = -1;
    private volatile ProducerToken requester = null;

    CriticalPathProducerMonitor(ProducerToken producerToken) {
      this.producerToken = producerToken;
    }

    long readyTimeUsec() {
      return readyNanos / 1000;
    }

    long startTimeUsec() {
      return startedNanos / 1000;
    }

    ProducerToken token() {
      return producerToken;
    }

    long cpuUsec() {
      return Math.max(durationNanos / 1000, 0);
    }

    private synchronized void completeIfStillRunning() {
      if (startedNanos != -1 && completedNanos == -1) {
        completed();
      }
    }

    long latencyUsec() {
      if (startedNanos == -1) {
        return 0L;
      } else if (completedNanos == -1) {
        return (elapsedNanos() - startedNanos) / 1000;
      }
      return latencyNanos() / 1000;
    }

    long endTimeUsec() {
      if (startedNanos == -1) {
        return 0L;
      } else if (completedNanos == -1) {
        return elapsedNanos() / 1000;
      }
      return completedNanos / 1000;
    }

    @Override
    public synchronized void ready() {
      this.readyNanos = elapsedNanos();
    }

    @Override
    public void requested() {
      ProducerToken requester = activeProducer.get();
      if (requester != null) {
        this.requester = requester;
      }
    }

    Optional<ProducerToken> getRequester() {
      return Optional.ofNullable(requester);
    }

    @Override
    public synchronized void methodStarting() {
      this.startedNanos = elapsedNanos();
      activeProducer.set(producerToken);
    }

    @Override
    public synchronized void methodFinished() {
      this.durationNanos = elapsedNanos() - startedNanos;
      activeProducer.set(null);
    }

    private synchronized void completed() {
      this.completedNanos = elapsedNanos();
    }

    private long elapsedNanos() {
      return ticker.read();
    }

    private long latencyNanos() {
      completeIfStillRunning();
      return completedNanos - startedNanos;
    }

    @Override
    public void succeeded(Object o) {
      completeIfStillRunning();
    }

    @Override
    public void failed(Throwable t) {
      completeIfStillRunning();
    }
  }
}
