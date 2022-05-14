package kvprog.common;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;
import javax.inject.Singleton;

@Module
public class InterceptorModule {

  @Singleton // Shared between all requests.
  @Provides
  @CallMetadata
  Multiset<String> provideRequestData() {
    return ConcurrentHashMultiset.create();
  }

  @Qualifier
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  public @interface CallMetadata {

  }
}
