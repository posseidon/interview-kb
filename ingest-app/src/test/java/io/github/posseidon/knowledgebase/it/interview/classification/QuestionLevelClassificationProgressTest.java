package io.github.posseidon.knowledgebase.it.interview.classification;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class QuestionLevelClassificationProgressTest {

  @Test
  void tryStartSucceedsWhenIdleAndResetsCounters() {
    QuestionLevelClassificationProgress progress =
        new QuestionLevelClassificationProgress(new SimpleMeterRegistry());

    assertThat(progress.tryStart()).isTrue();

    assertThat(progress.isRunning()).isTrue();
    assertThat(progress.total()).isZero();
    assertThat(progress.processed()).isZero();
    assertThat(progress.skipped()).isZero();
    assertThat(progress.failed()).isZero();
    assertThat(progress.error()).isNull();
  }

  @Test
  void tryStartFailsWhenAlreadyRunning() {
    QuestionLevelClassificationProgress progress =
        new QuestionLevelClassificationProgress(new SimpleMeterRegistry());
    progress.tryStart();

    assertThat(progress.tryStart()).isFalse();
  }

  @Test
  void advanceSkipAndRecordFailureAllCountTowardProcessed() {
    QuestionLevelClassificationProgress progress =
        new QuestionLevelClassificationProgress(new SimpleMeterRegistry());
    progress.tryStart();
    progress.begin(100);

    progress.advance(10);
    progress.skip(5);
    progress.recordFailure(3);

    assertThat(progress.total()).isEqualTo(100);
    assertThat(progress.processed()).isEqualTo(18);
    assertThat(progress.skipped()).isEqualTo(5);
    assertThat(progress.failed()).isEqualTo(3);
  }

  @Test
  void completeStopsRunningWithoutError() {
    QuestionLevelClassificationProgress progress =
        new QuestionLevelClassificationProgress(new SimpleMeterRegistry());
    progress.tryStart();

    progress.complete();

    assertThat(progress.isRunning()).isFalse();
    assertThat(progress.error()).isNull();
  }

  @Test
  void failStopsRunningAndRecordsError() {
    QuestionLevelClassificationProgress progress =
        new QuestionLevelClassificationProgress(new SimpleMeterRegistry());
    progress.tryStart();

    progress.fail("boom");

    assertThat(progress.isRunning()).isFalse();
    assertThat(progress.error()).isEqualTo("boom");
  }

  @Test
  void tryStartAfterCompletionResetsPreviousErrorAndCounters() {
    QuestionLevelClassificationProgress progress =
        new QuestionLevelClassificationProgress(new SimpleMeterRegistry());
    progress.tryStart();
    progress.begin(10);
    progress.advance(5);
    progress.skip(2);
    progress.recordFailure(3);
    progress.fail("previous failure");

    assertThat(progress.tryStart()).isTrue();

    assertThat(progress.total()).isZero();
    assertThat(progress.processed()).isZero();
    assertThat(progress.skipped()).isZero();
    assertThat(progress.failed()).isZero();
    assertThat(progress.error()).isNull();
  }

  @Test
  void activeGaugeReflectsRunningState() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    QuestionLevelClassificationProgress progress = new QuestionLevelClassificationProgress(registry);

    assertThat(registry.get("ingest.job.active")
        .tag("job", "skill_level_classification").gauge().value()).isZero();

    progress.tryStart();
    assertThat(registry.get("ingest.job.active")
        .tag("job", "skill_level_classification").gauge().value()).isEqualTo(1);

    progress.complete();
    assertThat(registry.get("ingest.job.active")
        .tag("job", "skill_level_classification").gauge().value()).isZero();
  }

  @Test
  void itemOutcomesAreCountedSeparately() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    QuestionLevelClassificationProgress progress = new QuestionLevelClassificationProgress(registry);
    progress.tryStart();

    progress.advance(4);
    progress.skip(2);
    progress.recordFailure(1);

    assertThat(registry.get("ingest.job.items")
        .tag("job", "skill_level_classification").tag("outcome", "succeeded")
        .counter().count()).isEqualTo(4);
    assertThat(registry.get("ingest.job.items")
        .tag("job", "skill_level_classification").tag("outcome", "skipped")
        .counter().count()).isEqualTo(2);
    assertThat(registry.get("ingest.job.items")
        .tag("job", "skill_level_classification").tag("outcome", "failed")
        .counter().count()).isEqualTo(1);
  }
}
