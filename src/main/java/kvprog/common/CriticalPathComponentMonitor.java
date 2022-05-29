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
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.inject.Inject;
import kvprog.CostList;
import kvprog.client.LoadGenerator;

/**
 * A monitor that calculates the critical path of execution.
 */
public final class CriticalPathComponentMonitor extends ProductionComponentMonitor {
  private static final Logger logger = Logger.getLogger(LoadGenerator.class.getName());

  // The name of the component being monitored, e.g. kvprog.toplevelserver.ServerProducerGraph.
  private final String componentName;
  // Timing information.
  private final Ticker ticker;
  // ProducerToken is a generated name for the Producer,
  //     e.g. kvprog.toplevelserver.ServerProducerGraph_ServerProducerModule_Put.
  // We create one CriticalPathProducerMonitor per token.
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

  /**
   * The factory that creates {@code ProductionComponentMonitor}. This is scoped because it collects data
   * throughout the request on behalf of each production component that's run. It creates one
   * {@code ProductionComponentMonitor} per component.
   */
  @CallScoped
  public static final class Factory extends ProductionComponentMonitor.Factory {

    private final ChildCostLists childCostLists;
    // Map of the component name to the monitor for it. This is a singleton unless you use SubComponents in your
    // graph of execution.
    private final Map<String, CriticalPathComponentMonitor> componentMonitors = new LinkedHashMap<>();
    private final ProductionExecutionOrderComponentMonitor.Factory productionExecutionMonitorFactory;
    private final Namer componentNamer;
    private final Ticker ticker;

    @Inject
    Factory(
        ChildCostLists childCostLists,
        ProductionExecutionOrderComponentMonitor.Factory productionExecutionMonitorFactory,
        Namer componentNamer) {
      this.childCostLists = childCostLists;
      this.productionExecutionMonitorFactory = productionExecutionMonitorFactory;
      this.componentNamer = componentNamer;
      this.ticker = Ticker.systemTicker();
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      CriticalPathComponentMonitor componentMonitor =
          new CriticalPathComponentMonitor(componentNamer.getName(component), ticker);
      componentMonitors.put(componentMonitor.componentName, componentMonitor);
      return componentMonitor;
    }

    @Nullable
    private CriticalPathProducerMonitor getProducerMonitor(ComponentProducerToken graphProducerToken) {
      CriticalPathComponentMonitor componentMonitor = componentMonitors.get(
          graphProducerToken.componentName());
      if (componentMonitor == null) {
        System.err.println("ComponentMonitor is null!");
        return null;
      }
      return componentMonitor.producerMonitors.get(graphProducerToken.producerToken());
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
      System.err.println("statsMonitors");
      componentMonitors.keySet().forEach(System.err::println);
      componentMonitors.values().forEach(System.err::println);
      if (componentMonitors.isEmpty()) {
        return CriticalPath.empty();
      }
      return singleThreadedCriticalPath(childCostLists);
    }

    /**
     * This algorithm is simplified by assuming execution is on a single request thread.
     */
    private CriticalPath singleThreadedCriticalPath(ChildCostLists childCostLists) {
      ImmutableList<ComponentProducerToken> executionOrder =
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

      ImmutableList<ComponentProducerToken> rpcCompletionOrder =
          getRpcNodesByEndTime(executionOrder.subList(0, index), childCostLists);
      ComponentProducerToken sink = executionOrder.get(index);

      return CriticalPath.create(
          CriticalPath.Node.create(
              sink.componentName(), // TODO(kas): Why sink.graphName()?
              0,
              getProducerMonitor(sink).endTimeUsec(),
              buildCriticalPathFromSink(
                  sink, index, rpcCompletionOrder, executionOrder, childCostLists)
                  .build()));
    }

    private CriticalPath.Builder buildCriticalPathFromSink(
        ComponentProducerToken sink,
        int index,
        ImmutableList<ComponentProducerToken> rpcCompletionOrder,
        ImmutableList<ComponentProducerToken> executionOrder,
        ChildCostLists childCostLists) {
      CriticalPath.Builder builder = CriticalPath.builder();
      ComponentProducerToken currentCriticalProducer = sink;
      List<ComponentProducerToken> criticalPath = new ArrayList<>();
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
        ComponentProducerToken rpcToken = null;
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
      ImmutableListMultimap<ComponentProducerToken, CostList> tokenToCostList =
          childCostLists.costLists();
      for (ComponentProducerToken token : criticalPath) {
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

    private ImmutableList<ComponentProducerToken> getRpcNodesByEndTime(
        ImmutableList<ComponentProducerToken> executionOrder, ChildCostLists childCostLists) {
      return executionOrder.stream()
          .filter(childCostLists::isRpcNode)
          .sorted(
              new Comparator<ComponentProducerToken>() {
                @Override
                public int compare(ComponentProducerToken a, ComponentProducerToken b) {
                  return Long.compare(
                      getProducerMonitor(a).endTimeUsec(), getProducerMonitor(b).endTimeUsec());
                }
              })
          .collect(ImmutableList.toImmutableList());
    }

    private CriticalPath.Node criticalPathNodeFromProducer(
        ImmutableList<CostList> childCostLists, ComponentProducerToken producer, long latencyUsec) {
      CriticalPathProducerMonitor producerRecorder = getProducerMonitor(producer);
      return CriticalPath.Node.builder()
          .name(Namer.producerName(producer.producerToken()))
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

  /**
   * One {@link CriticalPathProducerMonitor} exists per node in the Producer graph (generally a
   * function annotated `@Produces`).
   */
  final class CriticalPathProducerMonitor extends ProducerMonitor {
    // Unique ID for this ProducerMonitor.
    private final ProducerToken producerToken;
    // These variables are volatile since multiple threads can access, both producer threads and
    // the main request thread.
    // Time when the Producer method began.
    private volatile long startTimeNanos = -1;
    // Time when the Future completed successfully, or failed.
    private volatile long endTimeNanos = -1;
    // Duration is set when the Producer method finishes executing. CPU time is no longer taken,
    // but the future itself may not be ready.
    private volatile long durationNanos = -1;

    CriticalPathProducerMonitor(ProducerToken producerToken) {
      this.producerToken = producerToken;
    }

    @Override
    public synchronized void methodStarting() {
      startTimeNanos = ticker.read();
    }

    @Override
    public synchronized void methodFinished() {
      durationNanos = ticker.read() - startTimeNanos;
    }

    @Override
    public void succeeded(Object o) {
      endTimeNanos = ticker.read();
    }

    @Override
    public void failed(Throwable t) {
      endTimeNanos = ticker.read();
    }

    long startTimeUsec() {
      if (startTimeNanos == -1) {
        logger.info("Missing startTimeNanos on Token: " + producerToken);
        return 0L;
      }
      return startTimeNanos / 1000;
    }

    long cpuUsec() {
      if (durationNanos == -1) {
        // This can happen if you request critical path info within a Producer.
        logger.info("Missing duration on Token: " + producerToken + ". Assuming now().");
        durationNanos = ticker.read() - startTimeNanos;
      }
      return durationNanos / 1000;
    }

    long endTimeUsec() {
      return endTimeNanos() / 1000;
    }

    long latencyUsec() {
      if (startTimeNanos == -1) {
        logger.info("Missing startTimeNanos on Token: " + producerToken);
        return 0L;
      }
      return (endTimeNanos() - startTimeNanos) / 1000;
    }

    private long endTimeNanos() {
      if (endTimeNanos == -1) {
        // This can happen if you request critical path info within a Producer.
        logger.info("Missing endTimeNanos on Token: " + producerToken + ". Assuming now().");
        endTimeNanos = ticker.read();
      }
      return endTimeNanos;
    }
  }
}
