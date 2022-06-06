package kvprog.common;

import javax.inject.Inject;

/**
 * Supplies a {@code CriticalPathLedger}. Used if a critical path is
 * computed from some work that a producer delegates and needs to be recorded.
 *
 * <p>Upon calling {@code currentLedger()}, a {@code CriticalPathLedger} instance is created and
 * returned for the producer that is currently being executed and it always serves for that
 * producer, even if the ledger is used in another thread later on.
 *
 * <p>Usage:
 * <ul>
 *   <li>Inject {@code CriticalPathLedgerSupplier} to a {@code @Produces} method where a critial
 *       path is obtained.
 *   <li>Invoke its {@code currentLedger()} within the body of the same {@code @Produces} method to
 *       get a {@code CriticalPathLedger}.
 *   <li>The resulting ledger may be held on to, and it can be called later on, from a different
 *       thread.
 * </ul>
 *
 * <p>Example:
 *
 * <pre><code>
 * // Assumes a critical path is computed during RPC and is written to a field in
 * // GenericSearchResponse.
 * {@literal @}Produces
 * ListenableFuture&lt;GenericSearchResponse&gt; sendRpc(
 *     FooStub stub, CriticalPathLedgerSupplier supplier) {
 *   final CriticalPathLedger ledger = supplier.currentLedger();
 *   ListenableFuture&lt;GenericSearchResponse&gt; gsr = stub.sendRpc();
 *   return Futures.transform(gsr, new Function&lt;GenericSearchResponse, GenericSearchResponse&gt;(
 *     {@literal @}Override GenericSearchResponse apply(GenericSearchResponse gsr) {
 *       ledger.addCriticalPath(getCriticalPath(gsr));
 *       return gsr;
 *     }
 *   ));
 * }
 * </code></pre>
 */
public class CriticalPathLedgerSupplier {

  private final ChildCriticalPaths lists;

  @Inject
  CriticalPathLedgerSupplier(ChildCriticalPaths lists) {
    this.lists = lists;
  }

  /** Creates and returns the ledger for the producer that is currently being executed. */
  public CriticalPathLedger currentLedger() {
    return new CriticalPathLedger(ComponentProducerTokenContext.get(), lists);
  }
}
