package kvprog.common;

import com.google.auto.value.AutoValue;
import dagger.producers.monitoring.ProducerToken;

/**
 * A {@link ProducerToken} executing within a component.
 */
@AutoValue
abstract class ComponentProducerToken {

  static Builder builder() {
    return new AutoValue_ComponentProducerToken.Builder();
  }

  // The component graph within which this producer is executing.
  abstract String componentName();

  // The token of the producer this class represents.
  abstract ProducerToken producerToken();

  /**
   * Builder for {@link ComponentProducerToken}.
   */
  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setComponentName(String value);

    abstract Builder setProducerToken(ProducerToken value);

    abstract ComponentProducerToken build();
  }
}
