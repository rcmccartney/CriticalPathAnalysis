package kvprog.common;

import kvprog.CriticalPath;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;

/**
 * A ledger that accumulates critical paths within a producer's scope. Use {@link CriticalPathLedgerSupplier}
 * to obtain an instance.
 */
@ThreadSafe
public class CriticalPathLedger {
  private final ComponentProducerToken token;
  private final ChildCriticalPaths lists;

  CriticalPathLedger(ComponentProducerToken token, ChildCriticalPaths lists) {
    this.token = token;
    this.lists = lists;
  }

  /**
   * Records a critical path for work delegated by a producer from a remote system. Producers should
   * call this immediately after they compute the critical path.
   *
   * @param path Critical path computed by client teams within the work
   *             delegated by the current producer. All paths passed to this method will be considered
   *             critical to the producer, so if a producer calls several things in parallel, it is
   *             responsible for choosing the one that is critical.
   */
  public synchronized void addRemoteCriticalPath(CriticalPath path) {
    lists.addCriticalPath(token, path, true);
    recordRpcNode();
  }

  /**
   * Records a critical path with a single element containing the total amount of remote server time
   * for an RPC sent to a remote system. Total remote time will be the maximum of all calls.
   */
  public synchronized void addParallelRemoteRpcTimeCriticalPath(Duration time) {
    lists.addParallelRemoteRpcDuration(token, time);
    recordRpcNode();
  }

  /**
   * Records a critical path with a single element containing the total amount of remote server time
   * for an RPC sent to a remote system. Total remote time will be the sum of all calls.
   */
  public synchronized void addRemoteRpcTimeCriticalPath(Duration time) {
    lists.addRemoteRpcDuration(token, time);
    recordRpcNode();
  }

  /**
   * Records that this producer node issued an RPC.
   */
  public synchronized void recordRpcNode() {
    lists.recordRpcNode(token);
  }

  @Override
  public String toString() {
    return String.format("CriticalPathLedgerImpl: token: {%s} lists: {%s}", token, lists);
  }
}