package kvprog.common;

import com.google.auto.value.AutoValue;
import dagger.producers.monitoring.ProducerToken;

/**
 * A representation of a producer executing within a graph.
 */
@AutoValue
abstract class GraphProducerToken {

  static Builder builder() {
    return new AutoValue_GraphProducerToken.Builder();
  }

  abstract String graphName();

  abstract ProducerToken producerToken();

  abstract String tokenName();

  /**
   * Builder for GraphProducerToken.
   */
  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setGraphName(String value);

    abstract Builder setProducerToken(ProducerToken value);

    abstract Builder setTokenName(String value);

    abstract GraphProducerToken build();
  }
}
