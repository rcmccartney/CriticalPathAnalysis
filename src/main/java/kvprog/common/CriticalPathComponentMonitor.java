package kvprog.common;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import dagger.grpc.server.CallScoped;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import kvprog.CriticalPath;
import kvprog.client.LoadGenerator;
import kvprog.cserver.CServer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

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
  public static final class Factory extends ProductionComponentMonitor.Factory implements CriticalPathSupplier {
    private static final Logger c = Logger.getLogger(CServer.class.getName());

    private final ChildCriticalPaths childCriticalPaths;
    // Map of the component name to the monitor for it. This is a singleton unless you use SubComponents in your
    // graph of execution.
    private final Map<String, CriticalPathComponentMonitor> componentMonitors = new LinkedHashMap<>();
    private final ProductionExecutionOrderComponentMonitor.Factory productionExecutionMonitorFactory;
    private final Namer componentNamer;
    private final Ticker ticker;

    @Inject
    Factory(
        ChildCriticalPaths childCriticalPaths,
        ProductionExecutionOrderComponentMonitor.Factory productionExecutionMonitorFactory,
        Namer componentNamer) {
      this.childCriticalPaths = childCriticalPaths;
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
        logger.severe("ComponentMonitor is null!");
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
     *
     * <p>This algorithm is simplified by assuming execution is on a single request thread.
     */
    public synchronized InternalCriticalPath criticalPath() {
      if (componentMonitors.isEmpty()) {
        logger.severe("No Component Monitor found to calculate critical path!");
        return InternalCriticalPath.empty();
      }

      ImmutableList<ComponentProducerToken> executionOrder =
          productionExecutionMonitorFactory.getExecutionOrder();
      if (executionOrder.isEmpty()) {
        logger.severe("No execution order found to calculate critical path!");
        return InternalCriticalPath.empty();
      }

      int index = executionOrder.size() - 1;
      // There is no useful information from unstarted nodes, so we skip them.
      while (index > 0 && getProducerMonitor(executionOrder.get(index)).startTimeNanos() == 0) {
        logger.info("Skipping unstarted node: " + getProducerMonitor(executionOrder.get(index)));
        index--;
      }
      ComponentProducerToken sink = executionOrder.get(index);
      logger.info("Critical path start node: " + sink);
      ImmutableList<ComponentProducerToken> rpcCompletionOrder =
          executionOrder.subList(0, index).stream()
              .filter(childCriticalPaths::isRpcNode)
              .sorted(Comparator.comparingLong(cpt -> getProducerMonitor(cpt).endTimeNanos()))
              .collect(ImmutableList.toImmutableList());

      return InternalCriticalPath.create(
          InternalCriticalPath.Node.builder()
              .name(sink.componentName())
              .cpu(Duration.ofNanos(0))
              // If this is called in the sink, endTime will be now().
              .latency(Duration.ofNanos(getProducerMonitor(sink).endTimeNanos()))
              .childCriticalPath(buildCriticalPathFromSink(
                  sink, index, rpcCompletionOrder, executionOrder, childCriticalPaths))
              .build());
    }

    /**
     * Starting at the sink, work backwards through nodes in the order that they were executed
     * to build the critical path to the source. At each step, decide between adding the node that
     * started immediately prior to the current node or adding the most recently initiated RPC node
     * to the critical path. Make this decision using by minimizing the estimated slack time.
     */
    private InternalCriticalPath buildCriticalPathFromSink(
        ComponentProducerToken sink,
        int index,
        ImmutableList<ComponentProducerToken> rpcCompletionOrder,
        ImmutableList<ComponentProducerToken> executionOrder,
        ChildCriticalPaths childCriticalPaths) {
      InternalCriticalPath.Builder builder = InternalCriticalPath.builder();
      ComponentProducerToken currentCriticalProducer = sink;
      List<ComponentProducerToken> criticalPath = new ArrayList<>();
      criticalPath.add(currentCriticalProducer);

      int rpcIndex = rpcCompletionOrder.size() - 1;
      // Compute critical path.
      long criticalPathStartTimeNanos = getProducerMonitor(sink).startTimeNanos();
      while (index > 0) {
        currentCriticalProducer = executionOrder.get(--index);
        CriticalPathProducerMonitor pr = getProducerMonitor(currentCriticalProducer);

        // Choose between the previously executed node and the RPC
        // node whose end time is nearest, but prior to, the current
        // critical path start time.
        long cpuSlack = criticalPathStartTimeNanos - (pr.startTimeNanos() + pr.cpuNanos());
        long rpcSlack = Long.MAX_VALUE;
        ComponentProducerToken rpcToken = null;
        while (rpcIndex >= 0) {
          rpcToken = rpcCompletionOrder.get(rpcIndex);
          CriticalPathProducerMonitor newPr = getProducerMonitor(rpcToken);
          if (newPr.endTimeNanos() <= criticalPathStartTimeNanos) {
            rpcSlack = criticalPathStartTimeNanos - newPr.endTimeNanos();
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
        criticalPathStartTimeNanos = pr.startTimeNanos();
        criticalPath.add(currentCriticalProducer);
      }

      // Compute latency of each node in the critical path. The
      // latency of a node is the difference between its start time
      // and the smaller of its end time or the start time of its
      // child in the path. If there is a gap between the end time of
      // the current node and the start time of the next node, we add
      // that to a special framework latency symbol.
      long frameworkLatencyNanos = 0;
      CriticalPathProducerMonitor currentRecorder = null;
      CriticalPathProducerMonitor previousRecorder = null;
      ImmutableListMultimap<ComponentProducerToken, CriticalPath> tokenToCriticalPath =
          childCriticalPaths.criticalPaths();
      for (ComponentProducerToken token : criticalPath) {
        long latencyNanos;
        currentRecorder = getProducerMonitor(token);
        ImmutableList<CriticalPath> criticalPaths =
            tokenToCriticalPath.entries().stream()
                .filter(map -> map.getKey().producerToken().equals(token.producerToken()))
                .map(Map.Entry::getValue)
                .collect(ImmutableList.toImmutableList());

        if (previousRecorder == null) {
          latencyNanos = currentRecorder.latencyNanos();
        } else {
          long endTimeNanos =
              Math.min(previousRecorder.startTimeNanos(), currentRecorder.endTimeNanos());
          latencyNanos = Math.max(0, endTimeNanos - currentRecorder.startTimeNanos());
          // If current node is a RPC node and its end time is later than the start time of
          // previous(backward) node, then this RPC node is picked on CPU finish time basis.
          boolean isRpcNodePickedByCpuSlack =
              childCriticalPaths.isRpcNode(token)
                  && currentRecorder.endTimeNanos() > previousRecorder.startTimeNanos();
          if (isRpcNodePickedByCpuSlack) {
            // Introduce a child element to account for a portion of current rpc node's RPC time.
            // It's a gap between current rpc node's CPU finish time and the start time of the
            // previous(backward) node and it will replace the rpc node's original child elements.
            long rpcGapNanos =
                previousRecorder.startTimeNanos()
                    - (currentRecorder.startTimeNanos() + currentRecorder.cpuNanos());
            criticalPaths =
                rpcGapNanos > 0
                    ? childCriticalPathsFromLatencyNanos("rpc-gap", rpcGapNanos)
                    : ImmutableList.of();
          }
          frameworkLatencyNanos += previousRecorder.startTimeNanos() - endTimeNanos;
        }
        builder.addNode(criticalPathNodeFromProducer(criticalPaths, token, latencyNanos));
        previousRecorder = currentRecorder;
      }
      if (frameworkLatencyNanos > 0) {
        builder.addNode(
            InternalCriticalPath.Node.builder()
                .name(
                "<framework>")
                .cpu(Duration.ofNanos(frameworkLatencyNanos))
                .latency(Duration.ofNanos(frameworkLatencyNanos))
                .build());
      }

      return builder.build();
    }

    private InternalCriticalPath.Node criticalPathNodeFromProducer(
        ImmutableList<CriticalPath> childCriticalPaths, ComponentProducerToken producer, long latencyNanos) {
      CriticalPathProducerMonitor producerRecorder = getProducerMonitor(producer);
      return InternalCriticalPath.Node.builder()
          .name(Namer.producerName(producer.producerToken()))
          .cpu(Duration.ofNanos(Math.min(latencyNanos, producerRecorder.cpuNanos())))
          .latency(Duration.ofNanos(latencyNanos))
          .childCriticalPaths(childCriticalPaths)
          .build();
    }

    private ImmutableList<CriticalPath> childCriticalPathsFromLatencyNanos(
        String nodeName, long latencyNanos) {
      CriticalPath.Builder builder = CriticalPath.newBuilder();
      builder.addElement(InternalCriticalPath.newCostElement("/" + nodeName, Duration.ofNanos(latencyNanos)));
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

    long startTimeNanos() {
      if (startTimeNanos == -1) {
        logger.info("Missing startTimeNanos on Token: " + producerToken);
        return 0L;
      }
      return startTimeNanos;
    }

    long cpuNanos() {
      if (durationNanos == -1) {
        // This can happen if you request critical path info within a Producer.
        logger.info("Missing duration on Token: " + producerToken + ". Assuming now().");
        durationNanos = ticker.read() - startTimeNanos;
      }
      return durationNanos;
    }

    long latencyNanos() {
      if (startTimeNanos == -1) {
        logger.info("Missing startTimeNanos on Token: " + producerToken);
        return 0L;
      }
      return endTimeNanos() - startTimeNanos;
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
