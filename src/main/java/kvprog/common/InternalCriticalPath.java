package kvprog.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import kvprog.CriticalPath;
import kvprog.PathElement;

import java.time.Duration;

/**
 * Represents a critical path where each node has a unique name.
 */
@AutoValue
public abstract class InternalCriticalPath {

  public static InternalCriticalPath empty() {
    return new AutoValue_InternalCriticalPath(ImmutableList.of());
  }

  public static InternalCriticalPath create(Node node) {
    return new AutoValue_InternalCriticalPath(ImmutableList.of(node));
  }

  public static InternalCriticalPath create(ImmutableList<Node> nodes) {
    return new AutoValue_InternalCriticalPath(nodes);
  }

  public static Builder builder() {
    return new AutoValue_InternalCriticalPath.Builder();
  }

  public static PathElement newCostElement(String source, Duration cost) {
    return PathElement.newBuilder().setSource(source).setCostSec(Constants.durationToSec(cost)).build();
  }

  public static PathElement newCostElementFromSeconds(String source, double costSeconds) {
    return PathElement.newBuilder().setSource(source).setCostSec(costSeconds).build();
  }

  public abstract ImmutableList<Node> nodes();

  public final CriticalPath toCriticalPath() {
    CriticalPath.Builder builder = CriticalPath.newBuilder();
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
      CriticalPath.Builder builder,
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
    for (CriticalPath childCriticalPath : node.childCriticalPaths()) {
      for (PathElement childPathElement : childCriticalPath.getElementList()) {
        String childSource = childPathElement.getSource();
        String newChildSource =
            childSource.startsWith("/")
                ? prefix + childSource
                : String.format("%s/%s", prefix, childSource);
        // filtering of small elements from backend reports will be done in those backends,
        // so that we can get reporting at the root of each backend for small element filtering.
        builder.addElement(newCostElementFromSeconds(newChildSource, childPathElement.getCostSec()));
        totalLatency.plus(Constants.secToDuration(childPathElement.getCostSec()));
      }
    }
  }

  public static final class Builder {

    ImmutableList.Builder<Node> nodeListBuilder = ImmutableList.builder();

    public Builder addNode(Node node) {
      nodeListBuilder.add(node);
      return this;
    }

    public InternalCriticalPath build() {
      return InternalCriticalPath.create(nodeListBuilder.build());
    }
  }

  @AutoValue
  public abstract static class Node {

    public static Builder builder() {
      return new AutoValue_InternalCriticalPath_Node.Builder().childCriticalPath(InternalCriticalPath.empty()).childCriticalPaths(ImmutableList.of());
    }

    public abstract String name();

    public abstract Duration cpu();

    public abstract Duration latency();

    /**
     * Child critical path for computations internal to the server.
     */
    public abstract InternalCriticalPath childCriticalPath();

    /**
     * Child critical paths for computations external to the server. This is treated as a *leaf*
     * node of a critical path.
     */
    public abstract ImmutableList<CriticalPath> childCriticalPaths();

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder name(String value);

      public abstract Builder cpu(Duration value);

      public abstract Builder latency(Duration value);

      public abstract Builder childCriticalPath(InternalCriticalPath value);

      public abstract Builder childCriticalPaths(ImmutableList<CriticalPath> value);

      public abstract Node build();
    }
  }
}