package kvprog.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import kvprog.CostElement;
import kvprog.CostList;

import java.time.Duration;

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
  public static CostElement newCostElement(String source, Duration cost) {
    return CostElement.newBuilder().setSource(source).setCostSec(Constants.durationToSec(cost)).build();
  }

  public static CostElement newCostElementFromSeconds(String source, double costSeconds) {
    return CostElement.newBuilder().setSource(source).setCostSec(costSeconds).build();
  }

  public abstract ImmutableList<Node> nodes();

  public final CostList toCostList() {
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
   * @param node    a cost elements for this node and its children
   * @param prefix  added nodes should be added as slash-delimited children of this prefix.
   */
  private void addCostElements(
      CostList.Builder builder,
      Node node,
      String prefix) {
    // Add CostElement for node.
    builder.addElement(newCostElement(prefix, node.cpu()));

    // Recursively add CostElements for node's child critical path.
    Duration totalLatency = Duration.ofNanos(0);
    for (Node childNode : node.childCriticalPath().nodes()) {
      addCostElements(
          builder,
          childNode,
          String.format("%s/%s", prefix, childNode.name()));
      totalLatency.plus(childNode.latency());
    }

    // Add CostElements for node's child critical paths.
    for (CostList childCostList : node.childCostLists()) {
      for (CostElement childCostElement : childCostList.getElementList()) {
        String childSource = childCostElement.getSource();
        String newChildSource =
            childSource.startsWith("/")
                ? prefix + childSource
                : String.format("%s/%s", prefix, childSource);
        // filtering of small elements from backend reports will be done in those backends,
        // so that we can get reporting at the root of each backend for small element filtering.
        builder.addElement(newCostElementFromSeconds(newChildSource, childCostElement.getCostSec()));
        totalLatency.plus(Constants.secToDuration(childCostElement.getCostSec()));
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
      return new AutoValue_CriticalPath_Node.Builder().childCriticalPath(CriticalPath.empty()).childCostLists(ImmutableList.of());
    }

    /**
     * Returns the node's name.
     */
    public abstract String name();

    /**
     * Returns the node's CPU time.
     */
    public abstract Duration cpu();

    /**
     * Returns the node's latency.
     */
    public abstract Duration latency();

    /**
     * Child critical path for computations internal to the server.
     */
    public abstract CriticalPath childCriticalPath();

    /**
     * Child critical paths for computations external to the server. This is treated as a *leaf*
     * node of a critical path.
     */
    public abstract ImmutableList<CostList> childCostLists();

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder name(String value);

      public abstract Builder cpu(Duration value);

      public abstract Builder latency(Duration value);

      public abstract Builder childCriticalPath(CriticalPath value);

      public abstract Builder childCostLists(ImmutableList<CostList> value);

      public abstract Node build();
    }
  }
}