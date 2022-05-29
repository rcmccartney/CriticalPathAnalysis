package kvprog.common;

import kvprog.CostList;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A ledger that accumulates critical paths within a producer's scope. Use {@link CriticalPathLedgerSupplier}
 * to obtain an instance.
 */
@ThreadSafe
public class CriticalPathLedger {
  private final ComponentProducerToken token;
  private final ChildCostLists lists;

  CriticalPathLedger(ComponentProducerToken token, ChildCostLists lists) {
    this.token = token;
    this.lists = lists;
  }

  /**
   * Records a critical path for work delegated by a producer. Producers should call this
   * immediately after they compute the critical path.
   *
   * @param path Critical path computed by client teams (e.g., superroot team) within the work
   *     delegated by the current producer. All paths passed to this method will be considered
   *     critical to the producer, so if a producer calls several things in parallel, it is
   *     responsible for choosing the one that is critical.
   */
  public synchronized void addCriticalPath(CostList path) {
    lists.addCostList(token, path, false);
  }

  /**
   * Records a critical path for work delegated by a producer from a remote system. Producers should
   * call this immediately after they compute the critical path.
   *
   * @param path Critical path computed by client teams (e.g., superroot team) within the work
   *     delegated by the current producer. All paths passed to this method will be considered
   *     critical to the producer, so if a producer calls several things in parallel, it is
   *     responsible for choosing the one that is critical.
   */
  public synchronized void addRemoteCriticalPath(CostList path) {
    lists.addCostList(token, path, true);
    recordRpcNode();
  }

  /**
   * Records a critical path with a single element containing the total amount of remote server time
   * for an RPC sent to a remote system. Total remote time will be the maximum of all calls.
   */
  public synchronized void addParallelRemoteRpcTimeCriticalPath(double seconds) {
    lists.addParallelRemoteRpcSeconds(token, seconds);
    recordRpcNode();
  }

  /**
   * Records a critical path with a single element containing the total amount of remote server time
   * for an RPC sent to a remote system. Total remote time will be the sum of all calls.
   */
  public synchronized void addRemoteRpcTimeCriticalPath(double seconds) {
    lists.addRemoteRpcSeconds(token, seconds);
    recordRpcNode();
  }

  /** Records that this producer node issued an RPC. */
  public synchronized void recordRpcNode() {
    lists.recordRpcNode(token);
  }

  @Override
  public String toString() {
    return String.format("CriticalPathLedgerImpl: token: {%s} lists: {%s}", token, lists);
  }
}