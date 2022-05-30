package kvprog.common;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dagger.Module;
import dagger.Provides;
import io.grpc.Metadata;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Module
public class InterceptorModule {
  /**
   * Maps RPC parent id to latest RPC end time (in millis) so we can tell if an incoming RPC started
   * before the latest end time, from which we infer it was executed in parallel. There should only
   * be single-digit numbers of requests with Critical Path calculations per minute. We cap the size
   * at 1000 to limit memory use and expire after 1 minute to allow calculations for very slow
   * queries.
   */
  @Singleton // Shared between all requests.
  @Provides
  static Cache<Integer, Long> provideParallelRpcMonitor() {
    return CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(1)).build();
  }

  /**
   * This is a map from the span or trace id to the critical path for that request.
   * @return
   */
  @Singleton // Shared between all requests.
  @Provides
  @CriticalPaths
  static Map<Integer, CriticalPath> provideCriticalPaths() {
    return new HashMap<>();
  }

  @Singleton // Shared between all requests.
  @Provides
  @TraceId
  AtomicInteger provideTraceId() {
    return new AtomicInteger();
  }

  @Singleton
  @Provides
  @TraceIdKey
  Metadata.Key<String> provideTraceIdMetadataKey() {
    return Metadata.Key.of("trace_id", Metadata.ASCII_STRING_MARSHALLER);
  }

  @Singleton
  @Provides
  @ElapsedTimeKey
  Metadata.Key<String> provideElapsedTimeKey() {
    return Metadata.Key.of("elapsed_time", Metadata.ASCII_STRING_MARSHALLER);
  }

  @Singleton
  @Provides
  @CostListKey
  Metadata.Key<byte[]> provideCostListKey() {
    return Metadata.Key.of("cost_list-bin", Metadata.BINARY_BYTE_MARSHALLER);
  }

  @Singleton
  @Provides
  Ticker provideTicker() {
    return Ticker.systemTicker();
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface CriticalPaths {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface TraceId {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface TraceIdKey {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ElapsedTimeKey {

  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface CostListKey {

  }
}
