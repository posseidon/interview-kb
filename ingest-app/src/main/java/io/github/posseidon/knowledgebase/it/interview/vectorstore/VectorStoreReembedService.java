package io.github.posseidon.knowledgebase.it.interview.vectorstore;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Rebuilds every question's {@code vector_store} entry from scratch — needed whenever the
 * embedding model changes (e.g. Ollama's {@code nomic-embed-text} to Azure OpenAI's
 * {@code text-embedding-3-small}), since the old vectors live in a different embedding space and
 * silently produce wrong/degraded similarity search until replaced.
 *
 * <p>Runs as a single background job (there's only ever one at a time — see
 * {@link VectorStoreReembedProgress}), paged over the {@code question} table in batches so one
 * embedding request covers many questions at once (matching {@code QuestionUpsertService}'s
 * existing batch-add pattern) without holding the whole table in memory or one giant transaction
 * open for the entire run.
 */
@Service
public class VectorStoreReembedService {

  private static final Logger log = LoggerFactory.getLogger(VectorStoreReembedService.class);
  private static final String JOB = "vector_reembed";
  private static final int BATCH_SIZE = 50;

  private final QuestionRepository questionRepository;
  private final VectorStore vectorStore;
  private final VectorStoreReembedProgress progress;
  private final MeterRegistry meterRegistry;

  public VectorStoreReembedService(QuestionRepository questionRepository, VectorStore vectorStore,
      VectorStoreReembedProgress progress, MeterRegistry meterRegistry) {
    this.questionRepository = questionRepository;
    this.vectorStore = vectorStore;
    this.progress = progress;
    this.meterRegistry = meterRegistry;
  }

  /**
   * @return true if this call started a run; false if one was already in progress (no-op).
   */
  public boolean reembedAllAsync() {
    if (!progress.tryStart()) {
      return false;
    }
    Thread.ofVirtual().name("vector-reembed").start(this::reembedAll);
    return true;
  }

  private void reembedAll() {
    try {
      progress.begin(questionRepository.count());
      Pageable pageable = PageRequest.of(0, BATCH_SIZE);
      Page<Question> page;
      do {
        page = questionRepository.findAllWithSkills(pageable);
        reembedBatch(page.getContent());
        progress.advance(page.getNumberOfElements());
        pageable = pageable.next();
      } while (page.hasNext());
      progress.complete();
      log.info("Vector-store re-embedding finished: {} of {} questions", progress.processed(),
          progress.total());
    } catch (RuntimeException e) {
      log.error("Vector-store re-embedding failed after {} of {} questions", progress.processed(),
          progress.total(), e);
      progress.fail(e.getMessage());
    }
  }

  /**
   * Deletes each batch's stale entries before re-adding, mirroring
   * {@code QuestionUpsertService.saveQuestions}'s delete-then-add pattern for changed content: a
   * plain add for an id that already exists isn't guaranteed to overwrite it. Not wrapped in
   * {@code @Transactional} — {@link Question#getSkills()} is already eagerly fetched by
   * {@link QuestionRepository#findAllWithSkills}, so no open Hibernate session is needed here, and
   * delete+add don't need to be atomic with each other for an offline maintenance job (a brief gap
   * in {@code vector_store} for one question mid-run is an acceptable cost for a job that never
   * runs against a live user-facing path).
   */
  private void reembedBatch(List<Question> questions) {
    if (questions.isEmpty()) {
      return;
    }
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      List<String> ids = questions.stream().map(q -> q.getId().toString()).toList();
      vectorStore.delete(ids);
      List<Document> docs = questions.stream().map(QuestionDocuments::toDocument).toList();
      vectorStore.add(docs);
    } finally {
      sample.stop(meterRegistry.timer("ingest.job.call.duration", "job", JOB, "step", "embedBatch"));
    }
  }
}
