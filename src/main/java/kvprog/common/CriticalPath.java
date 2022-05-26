package kvprog.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import kvprog.CostElement;
import kvprog.CostList;

/**
 * Represents a critical path where each node has a unique name.
 */
@AutoValue
public abstract class CriticalPath {

  /**
   * Returns an empty critical path.
   */
  public static CriticalPath empty() {
    return new AutoValue_CriticalPath(ImmutableList.of());
  }

  /**
   * Returns a critical path with a single node.
   */
  public static CriticalPath create(Node node) {
    return new AutoValue_CriticalPath(ImmutableList.of(node));
  }

  /**
   * Returns a critical path with a list of nodes.
   */
  public static CriticalPath create(ImmutableList<Node> nodes) {
    return new AutoValue_CriticalPath(nodes);
  }

  /**
   * Returns a critical path builder.
   */
  public static Builder builder() {
    return new AutoValue_CriticalPath.Builder();
  }

  /**
   * Utility for constructing a single element of a critical path.
   */
  public static CostElement newCostElement(String source, double costSec) {
    return CostElement.newBuilder().setSource(source).setCostSec(costSec).build();
  }

  private static double usecToSec(long usec) {
    return usec / 1e6;
  }

  private static long secToUsec(double sec) {
    return Math.round(sec * 1e6);
  }

  public abstract ImmutableList<Node> nodes();

  public final CostList toCostList(long minMicrosecondsToIncludeInLog, boolean addUnattributed) {
    CostList.Builder builder = CostList.newBuilder();
    for (Node node : nodes()) {
      addCostElements(builder, node, "/" + node.name());
    }

    return builder.build();
  }

  /**
   * Recursively adds cost elements to the builder.
   *
   * @param builder the proto builder where cost elements will be added
   * @param node a cost elements for this node and its children
   * @param prefix added nodes should be added as slash-delimited children of this prefix.
   */
  private void addCostElements(
      CostList.Builder builder,
      Node node,
      String prefix) {
    long nodeCpuUsec = node.cpuUsec();
    double selfCostSecs = usecToSec(nodeCpuUsec);
    // Add CostElement for node.
    builder.addElement(newCostElement(prefix, selfCostSecs));

    // Recursively add CostElements for node's child critical path at GWS level.
    long latencyUsec = 0;
    for (Node childNode : node.childCriticalPath().nodes()) {
      addCostElements(
          builder,
          childNode,
          String.format("%s/%s", prefix, childNode.name()));
      latencyUsec += childNode.latencyUsec();
    }

    // Add CostElements for node's child critical path at levels below GWS.
    for (CostList childCostList : node.childCostLists()) {
      for (CostElement childCostElement : childCostList.getElementList()) {
        String childSource = childCostElement.getSource();
        String newChildSource =
            childSource.startsWith("/")
                ? prefix + childSource
                : String.format("%s/%s", prefix, childSource);
        // filtering of small elements from backend reports will be done in those backends,
        // so that we can get reporting at the root of each backend for small element filtering.
        builder.addElement(newCostElement(newChildSource, childCostElement.getCostSec()));
        latencyUsec += secToUsec(childCostElement.getCostSec());
      }
    }
  }

  /**
   * A builder for a critical path.
   */
  public static final class Builder {

    ImmutableList.Builder<Node> nodeListBuilder = ImmutableList.builder();

    /**
     * Adds a node to this critical path.
     */
    public Builder addNode(Node node) {
      nodeListBuilder.add(node);
      return this;
    }

    /**
     * Builds this critical path.
     */
    public CriticalPath build() {
      return CriticalPath.create(nodeListBuilder.build());
    }
  }

  /**
   * Node on a critical path.
   */
  @AutoValue
  public abstract static class Node {

    public static Builder builder() {
      return new AutoValue_CriticalPath_Node.Builder()
          .childCostLists(ImmutableList.of())
          .childCriticalPath(CriticalPath.empty());
    }

    /**
     * Creates a new node.
     */
    public static Node create(String name, long cpuUsec, long latencyUsec) {
      return builder().name(name).cpuUsec(cpuUsec).latencyUsec(latencyUsec).build();
    }

    /**
     * Creates a new node.
     */
    public static Node create(
        String name, long cpuUsec, long latencyUsec, CriticalPath childCriticalPath) {
      return builder()
          .name(name)
          .cpuUsec(cpuUsec)
          .latencyUsec(latencyUsec)
          .childCriticalPath(childCriticalPath)
          .build();
    }

    /**
     * Creates a new node.
     */
    public static Node create(
        String name, long cpuUsec, long latencyUsec, ImmutableList<CostList> childCostLists) {
      return builder()
          .name(name)
          .cpuUsec(cpuUsec)
          .latencyUsec(latencyUsec)
          .childCostLists(childCostLists)
          .build();
    }

    /**
     * Creates a new node.
     */
    public static Node create(
        String name,
        long cpuUsec,
        long latencyUsec,
        CriticalPath childCriticalPath,
        ImmutableList<CostList> childCostLists) {
      return builder()
          .name(name)
          .cpuUsec(cpuUsec)
          .latencyUsec(latencyUsec)
          .childCriticalPath(childCriticalPath)
          .childCostLists(childCostLists)
          .build();
    }

    /**
     * Returns the node's name.
     */
    public abstract String name();

    /**
     * Returns the node's CPU time in microseconds.
     */
    public abstract long cpuUsec();

    /**
     * Returns the node's latency in microseconds.
     */
    public abstract long latencyUsec();

    /**
     * Child critical path for computations internal to the server.
     */
    public abstract CriticalPath childCriticalPath();

    /**
     * Child critical paths for computations external to the server. This is treated as a *leaf*
     * node of a critical path.
     */
    public abstract ImmutableList<CostList> childCostLists();

    /**
     * Builder for Node
     */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder name(String value);

      public abstract Builder cpuUsec(long value);

      public abstract Builder latencyUsec(long value);

      public abstract Builder childCriticalPath(CriticalPath value);

      public abstract Builder childCostLists(ImmutableList<CostList> value);

      public abstract Node build();
    }
  }
}