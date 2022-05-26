package kvprog.common;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.AtomicLongMap;
import dagger.grpc.server.CallScoped;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import kvprog.CostList;

/**
 * A class to store all child critical paths and their root producer for a request. This must be a
 * request-scoped singleton because it stores this mutable state.
 */
@CallScoped
public final class ChildCostLists {

  private static final String REMOTE = "remote";
  private final Set<GraphProducerToken> tokensWithAttributedRemoteRpcTime = new HashSet<>();
  private final Set<GraphProducerToken> tokensWithRpcs = new HashSet<>();
  private final ImmutableListMultimap.Builder<GraphProducerToken, ChildCostList> costListsBuilder =
      ImmutableListMultimap.builder();
  private final AtomicLongMap<GraphProducerToken> remoteRpcUsecMap = AtomicLongMap.create();
  @Inject
  public ChildCostLists() {
  }

  private static double usecToSec(long usec) {
    return usec / 1e6;
  }

  private static long secToUsec(double sec) {
    return Math.round(sec * 1e6);
  }

  public synchronized void addCostList(
      GraphProducerToken token, CostList childCostList, boolean remote) {
    tokensWithAttributedRemoteRpcTime.add(token);
    costListsBuilder.put(token, ChildCostList.create(childCostList, remote));
  }

  public synchronized void addParallelRemoteRpcSeconds(GraphProducerToken token, double seconds) {
    remoteRpcUsecMap.getAndUpdate(token, oldValue -> Math.max(oldValue, secToUsec(seconds)));
  }

  public synchronized void addRemoteRpcSeconds(GraphProducerToken token, double seconds) {
    remoteRpcUsecMap.getAndAdd(token, secToUsec(seconds));
  }

  /**
   * Records that this producer node issued RPC(s).
   */
  public synchronized void recordRpcNode(GraphProducerToken token) {
    tokensWithRpcs.add(token);
  }

  /**
   * returns true if this producer node issued any RPC(s) during its execution.
   */
  public synchronized boolean isRpcNode(GraphProducerToken token) {
    return tokensWithRpcs.contains(token);
  }

  public synchronized ImmutableListMultimap<GraphProducerToken, CostList> costLists() {
    if (remoteRpcUsecMap.isEmpty()) {
      return ImmutableListMultimap.copyOf(
          Multimaps.transformValues(costListsBuilder.build(), ChildCostList::costList));
    }

    ImmutableListMultimap.Builder<GraphProducerToken, CostList> costListsWithRemoteBuilder =
        ImmutableListMultimap.builder();

    for (Map.Entry<GraphProducerToken, Collection<ChildCostList>> entry :
        costListsBuilder.build().asMap().entrySet()) {
      GraphProducerToken token = entry.getKey();
      costListsWithRemoteBuilder.putAll(token, createCostLists(token, entry.getValue()));
    }

    // Add a new cost list for all tokens that do not have any attributed time.
    for (Map.Entry<GraphProducerToken, Long> entry : remoteRpcUsecMap.asMap().entrySet()) {
      if (!tokensWithAttributedRemoteRpcTime.contains(entry.getKey())) {
        costListsWithRemoteBuilder.put(
            entry.getKey(), createCostListWithRemoteRpcSeconds(entry.getValue()));
      }
    }
    return costListsWithRemoteBuilder.build();
  }

  // Given all of the {@code childCostLists} for the given {@code token}, return a list of
  // {@code CostList} for the token.
  //
  // Any given producer may have issued zero or more RPCs, of which none, some, or all may have
  // returned CostLists.  This function ensures that the total latency of the remote CostLists
  // is greater than or equal to the total RPC time in the producer, as recorded in
  // {@code remoteRpcUsecMap}, by padding it out if necessary.
  //
  // TODO(b/190277126): Consider merging these return values into a single CostList.
  private ImmutableList<CostList> createCostLists(
      GraphProducerToken token, Collection<ChildCostList> childCostLists) {
    if (childCostLists.isEmpty()) {
      return ImmutableList.of();
    }

    // If the total RPC time is not known, we have no choice but to trust the childCostLists.
    // Return them as they are.
    Long remoteRpcUsec = remoteRpcUsecMap.get(token);
    if (remoteRpcUsec == null) {
      return childCostLists.stream().map(p -> p.costList()).collect(toImmutableList());
    }

    // Sum up the total time in all the remote childCostLists, and compare it to the total RPC time.
    long attributedUsec = 0;
    for (ChildCostList costList : childCostLists) {
      if (costList.remote()) {
        attributedUsec +=
            costList.costList().getElementList().stream()
                .mapToLong(e -> secToUsec(e.getCostSec()))
                .sum();
      }
    }
    long unattributedRpcUsec = remoteRpcUsec - attributedUsec;

    // If the total RPC time exceeds the total time in the childCostLists, there were some RPCs
    // that did not return CostLists, or there was some overhead in the RPC itself, or both. Add
    // that unaccounted time in a new CostList.
    if (unattributedRpcUsec > 0) {
      return Stream.concat(
              childCostLists.stream().map(p -> p.costList()),
              Stream.of(createCostListWithRemoteRpcSeconds(unattributedRpcUsec)))
          .collect(toImmutableList());
    }

    // If the total RPC time is equal to the total time in the remote childCostLists, that's great!
    // We can return them as they are.
    //
    // If the total RPC time is less than the total time in the remote childCostLists, we also
    // return them as they are, for the time being.
    //
    //   * If the RPCs that generated the childCostLists were sequential, this is a reasonable
    //     solution: essentially, we are resolving a conflict between GWS and its backends by
    //     trusting the backends. The discrepancy is likely small.
    //   * If the RPCs that generated the childCostLists were parallel, this is incorrect: only the
    //     longest of them was on the critical path, and the others should be dropped.
    //   * If there were multiple sequential stages of parallel RPCs, the longest in each stage
    //     is on the critical path, and the others should be dropped.
    //
    // Right now, we don't have any signals to distinguish those various cases, so we are assuming
    // the RPCs were sequential.  That is the longstanding behavior, and likely the common case.
    //
    // TODO(b/190277126): Add signals that help distinguish those cases, and handle them here.
    return childCostLists.stream().map(p -> p.costList()).collect(toImmutableList());
  }

  private CostList createCostListWithRemoteRpcSeconds(long serverElapsedUsec) {
    CostList.Builder costListWithRemoteBuilder = CostList.newBuilder();
    costListWithRemoteBuilder
        .addElementBuilder()
        .setCostSec(usecToSec(serverElapsedUsec))
        .setSource(REMOTE);
    return costListWithRemoteBuilder.build();
  }

  /**
   * Value object that stores a {@code CostList} and whether or not the critical path data
   * originated from a remote system.
   */
  @AutoValue
  public abstract static class ChildCostList {

    public static ChildCostList create(CostList costList, boolean remote) {
      return new AutoValue_ChildCostLists_ChildCostList(costList, remote);
    }

    public abstract CostList costList();

    /**
     * Returns true if this {@code CostList} was retrieved from a remote system.
     */
    public abstract boolean remote();
  }
}
