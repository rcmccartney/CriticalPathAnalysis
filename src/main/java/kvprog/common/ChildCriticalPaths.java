package kvprog.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.AtomicLongMap;
import dagger.grpc.server.CallScoped;
import kvprog.CriticalPath;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A class to store all child critical paths and their root producer for a request. This must be a
 * request-scoped singleton because it stores this mutable state.
 */
@CallScoped
public final class ChildCriticalPaths {

  private static final String REMOTE = "remote";
  private final Set<ComponentProducerToken> tokensWithAttributedRemoteRpcTime = new HashSet<>();
  private final Set<ComponentProducerToken> tokensWithRpcs = new HashSet<>();
  private final ImmutableListMultimap.Builder<ComponentProducerToken, ChildCriticalPath> criticalPathsBuilder =
      ImmutableListMultimap.builder();
  private final AtomicLongMap<ComponentProducerToken> remoteRpcNanosMap = AtomicLongMap.create();

  @Inject
  public ChildCriticalPaths() {
  }

  public synchronized void addCriticalPath(
      ComponentProducerToken token, CriticalPath childCriticalPath, boolean remote) {
    tokensWithAttributedRemoteRpcTime.add(token);
    criticalPathsBuilder.put(token, ChildCriticalPath.create(childCriticalPath, remote));
  }

  public synchronized void addParallelRemoteRpcDuration(ComponentProducerToken token, Duration duration) {
    remoteRpcNanosMap.getAndUpdate(token, oldValue -> Math.max(oldValue, duration.toNanos()));
  }

  public synchronized void addRemoteRpcDuration(ComponentProducerToken token, Duration duration) {
    remoteRpcNanosMap.getAndAdd(token, duration.toNanos());
  }

  /**
   * Records that this producer node issued RPC(s).
   */
  public synchronized void recordRpcNode(ComponentProducerToken token) {
    tokensWithRpcs.add(token);
  }

  /**
   * returns true if this producer node issued any RPC(s) during its execution.
   */
  public synchronized boolean isRpcNode(ComponentProducerToken token) {
    return tokensWithRpcs.contains(token);
  }

  public synchronized ImmutableListMultimap<ComponentProducerToken, CriticalPath> criticalPaths() {
    if (remoteRpcNanosMap.isEmpty()) {
      return ImmutableListMultimap.copyOf(
          Multimaps.transformValues(criticalPathsBuilder.build(), ChildCriticalPath::criticalPath));
    }

    ImmutableListMultimap.Builder<ComponentProducerToken, CriticalPath> criticalPathsWithRemoteBuilder =
        ImmutableListMultimap.builder();

    for (Map.Entry<ComponentProducerToken, Collection<ChildCriticalPath>> entry :
        criticalPathsBuilder.build().asMap().entrySet()) {
      ComponentProducerToken token = entry.getKey();
      criticalPathsWithRemoteBuilder.putAll(token, createCriticalPaths(token, entry.getValue()));
    }

    // Add a new cost list for all tokens that do not have any attributed time.
    for (Map.Entry<ComponentProducerToken, Long> entry : remoteRpcNanosMap.asMap().entrySet()) {
      if (!tokensWithAttributedRemoteRpcTime.contains(entry.getKey())) {
        criticalPathsWithRemoteBuilder.put(
            entry.getKey(), createCriticalPathWithRemoteRpcDuration(Duration.ofNanos(entry.getValue())));
      }
    }
    return criticalPathsWithRemoteBuilder.build();
  }

  // Given all of the {@code childCriticalPaths} for the given {@code token}, return a list of
  // {@code CriticalPath} for the token.
  //
  // Any given producer may have issued zero or more RPCs, of which none, some, or all may have
  // returned CriticalPaths.  This function ensures that the total latency of the remote CriticalPaths
  // is greater than or equal to the total RPC time in the producer, as recorded in
  // {@code remoteRpcNanosMap}, by padding it out if necessary.
  //
  // TODO: Consider merging these return values into a single CriticalPath.
  private ImmutableList<CriticalPath> createCriticalPaths(
      ComponentProducerToken token, Collection<ChildCriticalPath> childCriticalPaths) {
    if (childCriticalPaths.isEmpty()) {
      return ImmutableList.of();
    }

    // If the total RPC time is not known, we have no choice but to trust the childCriticalPaths.
    // Return them as they are.
    Long remoteRpcNanos = remoteRpcNanosMap.get(token);
    if (remoteRpcNanos == null) {
      return childCriticalPaths.stream().map(p -> p.criticalPath()).collect(toImmutableList());
    }

    // Sum up the total time in all the remote childCriticalPaths, and compare it to the total RPC time.
    long attributedNanos = 0;
    for (ChildCriticalPath criticalPath : childCriticalPaths) {
      if (criticalPath.remote()) {
        attributedNanos +=
            criticalPath.criticalPath().getElementList().stream()
                .mapToLong(e -> Constants.secToNanos(e.getCostSec()))
                .sum();
      }
    }
    long unattributedRpcNanos = remoteRpcNanos - attributedNanos;

    // If the total RPC time exceeds the total time in the childCriticalPaths, there were some RPCs
    // that did not return CriticalPaths, or there was some overhead in the RPC itself, or both. Add
    // that unaccounted time in a new CriticalPath.
    if (unattributedRpcNanos > 0) {
      return Stream.concat(
              childCriticalPaths.stream().map(p -> p.criticalPath()),
              Stream.of(createCriticalPathWithRemoteRpcDuration(Duration.ofNanos(unattributedRpcNanos))))
          .collect(toImmutableList());
    }

    // If the total RPC time is equal to the total time in the remote childCriticalPaths, that's great!
    // We can return them as they are.
    //
    // If the total RPC time is less than the total time in the remote childCriticalPaths, we also
    // return them as they are, for the time being.
    //
    //   * If the RPCs that generated the childCriticalPaths were sequential, this is a reasonable
    //     solution: essentially, we are resolving a conflict between GWS and its backends by
    //     trusting the backends. The discrepancy is likely small.
    //   * If the RPCs that generated the childCriticalPaths were parallel, this is incorrect: only the
    //     longest of them was on the critical path, and the others should be dropped.
    //   * If there were multiple sequential stages of parallel RPCs, the longest in each stage
    //     is on the critical path, and the others should be dropped.
    //
    // Right now, we don't have any signals to distinguish those various cases, so we are assuming
    // the RPCs were sequential.  That is the longstanding behavior, and likely the common case.
    //
    // TODO: Add signals that help distinguish those cases, and handle them here.
    return childCriticalPaths.stream().map(p -> p.criticalPath()).collect(toImmutableList());
  }

  private CriticalPath createCriticalPathWithRemoteRpcDuration(Duration serverElapsedTime) {
    CriticalPath.Builder criticalPathWithRemoteBuilder = CriticalPath.newBuilder();
    criticalPathWithRemoteBuilder
        .addElementBuilder()
        .setCostSec(Constants.durationToSec(serverElapsedTime))
        .setSource(REMOTE);
    return criticalPathWithRemoteBuilder.build();
  }

  /**
   * Value object that stores a {@code CriticalPath} and whether or not the critical path data
   * originated from a remote system.
   */
  @AutoValue
  public abstract static class ChildCriticalPath {

    public static ChildCriticalPath create(CriticalPath criticalPath, boolean remote) {
      return new AutoValue_ChildCriticalPaths_ChildCriticalPath(criticalPath, remote);
    }

    public abstract CriticalPath criticalPath();

    /**
     * Returns true if this {@code CriticalPath} was retrieved from a remote system.
     */
    public abstract boolean remote();
  }
}
