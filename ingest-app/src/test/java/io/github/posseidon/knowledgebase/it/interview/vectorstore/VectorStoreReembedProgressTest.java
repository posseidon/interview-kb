package io.github.posseidon.knowledgebase.it.interview.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class VectorStoreReembedProgressTest {

  @Test
  void tryStartSucceedsWhenIdleAndResetsCounters() {
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(new SimpleMeterRegistry());

    assertThat(progress.tryStart()).isTrue();

    assertThat(progress.isRunning()).isTrue();
    assertThat(progress.total()).isZero();
    assertThat(progress.processed()).isZero();
    assertThat(progress.error()).isNull();
  }

  @Test
  void tryStartFailsWhenAlreadyRunning() {
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(new SimpleMeterRegistry());
    progress.tryStart();

    assertThat(progress.tryStart()).isFalse();
  }

  @Test
  void beginAndAdvanceTrackCounts() {
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(new SimpleMeterRegistry());
    progress.tryStart();

    progress.begin(100);
    progress.advance(30);
    progress.advance(20);

    assertThat(progress.total()).isEqualTo(100);
    assertThat(progress.processed()).isEqualTo(50);
  }

  @Test
  void completeStopsRunningWithoutError() {
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(new SimpleMeterRegistry());
    progress.tryStart();

    progress.complete();

    assertThat(progress.isRunning()).isFalse();
    assertThat(progress.error()).isNull();
  }

  @Test
  void failStopsRunningAndRecordsError() {
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(new SimpleMeterRegistry());
    progress.tryStart();

    progress.fail("boom");

    assertThat(progress.isRunning()).isFalse();
    assertThat(progress.error()).isEqualTo("boom");
  }

  @Test
  void tryStartAfterCompletionResetsPreviousErrorAndCounters() {
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(new SimpleMeterRegistry());
    progress.tryStart();
    progress.begin(10);
    progress.advance(10);
    progress.fail("previous failure");

    assertThat(progress.tryStart()).isTrue();

    assertThat(progress.total()).isZero();
    assertThat(progress.processed()).isZero();
    assertThat(progress.error()).isNull();
  }

  @Test
  void activeGaugeReflectsRunningState() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(registry);

    assertThat(registry.get("ingest.job.active").tag("job", "vector_reembed").gauge().value())
        .isZero();

    progress.tryStart();
    assertThat(registry.get("ingest.job.active").tag("job", "vector_reembed").gauge().value())
        .isEqualTo(1);

    progress.complete();
    assertThat(registry.get("ingest.job.active").tag("job", "vector_reembed").gauge().value())
        .isZero();
  }

  @Test
  void advanceIncrementsSucceededItemsCounter() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(registry);
    progress.tryStart();

    progress.advance(3);
    progress.advance(2);

    assertThat(registry.get("ingest.job.items")
        .tag("job", "vector_reembed").tag("outcome", "succeeded").counter().count())
        .isEqualTo(5);
  }

  @Test
  void completeAndFailIncrementRunsCounterByOutcome() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    VectorStoreReembedProgress progress = new VectorStoreReembedProgress(registry);

    progress.tryStart();
    progress.complete();
    progress.tryStart();
    progress.fail("boom");

    assertThat(registry.get("ingest.job.runs")
        .tag("job", "vector_reembed").tag("outcome", "completed").counter().count())
        .isEqualTo(1);
    assertThat(registry.get("ingest.job.runs")
        .tag("job", "vector_reembed").tag("outcome", "failed").counter().count())
        .isEqualTo(1);
  }
}
