package io.github.posseidon.knowledgebase.it.interview.classification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Tracks the single, app-wide skill-level classification run — same mutual-exclusion shape as
 * {@link VectorStoreReembedProgress}, plus per-item outcome counters: a question can be skipped
 * (no skills assigned, nothing to classify against) or fail classification without aborting the
 * whole run, unlike a job-level {@link #fail(String)}.
 */
@Component
public class QuestionLevelClassificationProgress {

  private static final String JOB = "skill_level_classification";

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong total = new AtomicLong(0);
  private final AtomicLong processed = new AtomicLong(0);
  private final AtomicLong skipped = new AtomicLong(0);
  private final AtomicLong failed = new AtomicLong(0);
  private volatile String error;

  private final MeterRegistry meterRegistry;

  public QuestionLevelClassificationProgress(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder("ingest.job.active", running, r -> r.get() ? 1 : 0)
        .tag("job", JOB).register(meterRegistry);
    Gauge.builder("ingest.job.total", total, AtomicLong::get).tag("job", JOB).register(meterRegistry);
    Gauge.builder("ingest.job.processed", processed, AtomicLong::get)
        .tag("job", JOB).register(meterRegistry);
  }

  /**
   * @return true if this call claimed the run (caller should proceed); false if one was already
   * in progress.
   */
  public boolean tryStart() {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    total.set(0);
    processed.set(0);
    skipped.set(0);
    failed.set(0);
    error = null;
    return true;
  }

  public void begin(long totalCount) {
    total.set(totalCount);
  }

  /**
   * Records successfully classified questions.
   */
  public void advance(long count) {
    processed.addAndGet(count);
    items("succeeded").increment(count);
  }

  /**
   * Records questions skipped for having no skills assigned.
   */
  public void skip(long count) {
    processed.addAndGet(count);
    skipped.addAndGet(count);
    items("skipped").increment(count);
  }

  /**
   * Records questions whose classification call/parse failed — the level is left untouched, and
   * the run keeps going.
   */
  public void recordFailure(long count) {
    processed.addAndGet(count);
    failed.addAndGet(count);
    items("failed").increment(count);
  }

  public void complete() {
    running.set(false);
    meterRegistry.counter("ingest.job.runs", "job", JOB, "outcome", "completed").increment();
  }

  public void fail(String message) {
    error = message;
    running.set(false);
    meterRegistry.counter("ingest.job.runs", "job", JOB, "outcome", "failed").increment();
  }

  private Counter items(String outcome) {
    return meterRegistry.counter("ingest.job.items", "job", JOB, "outcome", outcome);
  }

  public boolean isRunning() {
    return running.get();
  }

  public long total() {
    return total.get();
  }

  public long processed() {
    return processed.get();
  }

  public long skipped() {
    return skipped.get();
  }

  public long failed() {
    return failed.get();
  }

  public String error() {
    return error;
  }
}
