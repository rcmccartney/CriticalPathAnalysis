package kvprog.common;

/**
 * A thread-scoped {@link ComponentProducerToken}.  This gives us the currently executing Producer for any thread.
 */
public final class ComponentProducerTokenContext {
  private static final ThreadLocal<ComponentProducerToken> context = new ThreadLocal<>();

  public static void set(ComponentProducerToken token) {
    context.set(token);
  }

  public static ComponentProducerToken get() {
    return context.get();
  }

  public static void remove() {
    context.remove();
  }
}
