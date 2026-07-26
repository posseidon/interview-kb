package io.github.posseidon.knowledgebase.it.interview.vectorstore;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Tracks the single, app-wide vector-store re-embedding run (there's only ever one at a time —
 * this is an admin maintenance action, not a per-request/per-session job). {@link #tryStart()}
 * is the mutual-exclusion gate: a second trigger while one is already running is a no-op.
 */
@Component
public class VectorStoreReembedProgress {

  private static final String JOB = "vector_reembed";

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong total = new AtomicLong(0);
  private final AtomicLong processed = new AtomicLong(0);
  private volatile String error;
  private final MeterRegistry meterRegistry;

  public VectorStoreReembedProgress(MeterRegistry meterRegistry) {
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
    error = null;
    return true;
  }

  public void begin(long totalCount) {
    total.set(totalCount);
  }

  public void advance(long count) {
    processed.addAndGet(count);
    meterRegistry.counter("ingest.job.items", "job", JOB, "outcome", "succeeded").increment(count);
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

  public boolean isRunning() {
    return running.get();
  }

  public long total() {
    return total.get();
  }

  public long processed() {
    return processed.get();
  }

  public String error() {
    return error;
  }
}
