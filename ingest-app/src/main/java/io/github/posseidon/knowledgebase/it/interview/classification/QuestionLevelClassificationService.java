package io.github.posseidon.knowledgebase.it.interview.classification;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.vectorstore.VectorStoreReembedService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Reclassifies every question's {@link Question#getLevel()} from the descriptions and per-level
 * criteria of its assigned {@link Skill}(s), instead of leaving it at whatever ingestion set (often
 * just the {@code NOVICE} default). One real LLM call is needed per question (delegated to
 * {@link QuestionLevelClassifier}) — unlike {@link VectorStoreReembedService}'s batched embedding
 * calls, this can't be batched across questions since each has its own skill set and prompt — so a
 * page's questions are classified with bounded concurrency ({@link #CONCURRENCY} virtual threads at
 * a time) rather than one at a time, to keep a full-table run from taking too long.
 *
 * <p>Questions with no skills assigned are skipped (there's no criteria to classify against); a
 * failed/unparseable call for one question is logged and counted, but never aborts the run.
 */
@Service
public class QuestionLevelClassificationService {

  private static final Logger log = LoggerFactory.getLogger(QuestionLevelClassificationService.class);
  private static final int BATCH_SIZE = 50;
  private static final int CONCURRENCY = 6;

  private final QuestionRepository questionRepository;
  private final QuestionLevelClassifier classifier;
  private final QuestionLevelClassificationProgress progress;

  public QuestionLevelClassificationService(QuestionRepository questionRepository,
      QuestionLevelClassifier classifier, QuestionLevelClassificationProgress progress) {
    this.questionRepository = questionRepository;
    this.classifier = classifier;
    this.progress = progress;
  }

  /**
   * @return true if this call started a run; false if one was already in progress (no-op).
   */
  public boolean classifyAllAsync() {
    if (!progress.tryStart()) {
      return false;
    }
    Thread.ofVirtual().name("skill-level-classify").start(this::classifyAll);
    return true;
  }

  private void classifyAll() {
    try {
      progress.begin(questionRepository.count());
      try (ExecutorService executor =
          Executors.newFixedThreadPool(CONCURRENCY, Thread.ofVirtual().factory())) {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        Page<Question> page;
        do {
          page = questionRepository.findAllWithSkills(pageable);
          awaitAll(page.getContent().stream().map(q -> executor.submit(() -> classifyOne(q))).toList());
          pageable = pageable.next();
        } while (page.hasNext());
      }
      progress.complete();
      log.info("Skill-level classification finished: {} of {} questions ({} skipped, {} failed)",
          progress.processed(), progress.total(), progress.skipped(), progress.failed());
    } catch (RuntimeException e) {
      log.error("Skill-level classification failed after {} of {} questions",
          progress.processed(), progress.total(), e);
      progress.fail(e.getMessage());
    }
  }

  private void awaitAll(List<? extends Future<?>> futures) {
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e.getCause());
      }
    }
  }

  private void classifyOne(Question question) {
    if (question.getSkills().isEmpty()) {
      progress.skip(1);
      return;
    }

    Optional<QuestionLevelClassification> result = classifier.classify(question);
    if (result.isEmpty()) {
      progress.recordFailure(1);
      return;
    }

    question.setLevel(result.get().level());
    questionRepository.save(question);
    progress.advance(1);
    log.info("Classified question {} as {}: {}", question.getId(), result.get().level(),
        result.get().rationale());
  }
}
