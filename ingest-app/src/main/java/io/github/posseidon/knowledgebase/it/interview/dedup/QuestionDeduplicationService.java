package io.github.posseidon.knowledgebase.it.interview.dedup;

import io.github.posseidon.knowledgebase.it.interview.classification.QuestionLevelClassification;
import io.github.posseidon.knowledgebase.it.interview.classification.QuestionLevelClassifier;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.merge.MergeService;
import io.github.posseidon.knowledgebase.it.interview.metrics.LlmTokenMetrics;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import io.github.posseidon.knowledgebase.it.interview.util.VectorStoreIds;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Auto-merges near-duplicate questions found during ingestion. For each newly-created question
 * from a single {@code /ingest} (or {@code /interviews}) call, looks for an already-existing
 * question that's a near-exact semantic match in {@code vector_store}; if one is found, combines
 * the two questions' wording via an LLM rephrase call (see
 * {@code prompts/question-merge-system.st} — grounded strictly in the two existing questions, no
 * new information), reclassifies the merged question's level, and folds the new question into the
 * existing one using {@link MergeService}'s audit-logged merge mechanics (snapshot, answer/skill
 * union, frequency increment, hard-delete of the absorbed row).
 *
 * <p>Runs entirely in the background (bounded concurrency, {@link #CONCURRENCY} virtual threads at
 * a time) after the synchronous upsert path has already created the row and embedded its raw
 * content — so a caller of {@code /ingest} never waits on this. Unlike
 * {@code VectorStoreReembedService}/{@code QuestionLevelClassificationService}, this isn't a
 * singleton whole-table job with its own progress tracker: each ingest call's dedup pass only
 * touches that call's own newly-created ids, and multiple calls' passes can safely run
 * concurrently since they never touch the same "new" rows.
 *
 * <p><b>Known limitation:</b> if two questions in the very same ingest batch are near-duplicates of
 * <em>each other</em> (neither one pre-existing), this pass deliberately does not merge them —
 * matching against another brand-new row from the same run would require coordinating which one
 * "wins" as the target, which isn't worth the complexity for what should be a rare case (an exact
 * duplicate within one batch is normally already caught by {@code QuestionUpsertService}'s
 * content-hash check). Two different new questions that both happen to match the very same
 * existing target concurrently are also not coordinated with each other; a lost update there is
 * possible but rare, and self-corrects on the next ingest touching that question, so no
 * additional locking has been added for it — see the class Javadoc, not a silent gap.
 */
@Service
public class QuestionDeduplicationService {

  private static final Logger log = LoggerFactory.getLogger(QuestionDeduplicationService.class);
  private static final String JOB = "question_dedup";
  private static final float EXACT_MATCH_THRESHOLD = 0.95f;
  /**
   * Matches at or above this (but below {@link #EXACT_MATCH_THRESHOLD}) are too uncertain to
   * auto-merge without review, but too close to ignore — flagged as a candidate for the existing
   * human-reviewed {@code /merge} flow instead of being silently dropped. Matches
   * {@code MergeController}'s own default {@code threshold} query param, so "flagged during
   * ingestion" and "shows up in a default /merge/candidates call" mean the same bar.
   */
  private static final float REVIEW_THRESHOLD = 0.7f;
  private static final int CONCURRENCY = 6;
  /**
   * Just enough candidates to find the single best pre-existing match past the question's own
   * (near-certain) self-match in {@code vector_store} — not a broad top-N search.
   */
  private static final int SEARCH_TOP_K = 3;

  private final QuestionRepository questionRepository;
  private final VectorStore vectorStore;
  private final MergeService mergeService;
  private final QuestionLevelClassifier classifier;
  private final ChatClient chatClient;
  private final MeterRegistry meterRegistry;
  private final LlmTokenMetrics tokenMetrics;
  private final Resource mergeSystemPromptResource;

  public QuestionDeduplicationService(QuestionRepository questionRepository, VectorStore vectorStore,
      MergeService mergeService, QuestionLevelClassifier classifier, ChatClient chatClient,
      MeterRegistry meterRegistry, LlmTokenMetrics tokenMetrics,
      @Value("classpath:prompts/question-merge-system.st") Resource mergeSystemPromptResource) {
    this.questionRepository = questionRepository;
    this.vectorStore = vectorStore;
    this.mergeService = mergeService;
    this.classifier = classifier;
    this.chatClient = chatClient;
    this.meterRegistry = meterRegistry;
    this.tokenMetrics = tokenMetrics;
    this.mergeSystemPromptResource = mergeSystemPromptResource;
  }

  /**
   * Fire-and-forget: never blocks the caller. A no-op for an empty/null list.
   */
  public void deduplicateAsync(List<UUID> newQuestionIds) {
    if (newQuestionIds == null || newQuestionIds.isEmpty()) {
      return;
    }
    Set<UUID> newIds = new HashSet<>(newQuestionIds);
    Thread.ofVirtual().name("question-dedup").start(() -> deduplicate(newIds));
  }

  private void deduplicate(Set<UUID> newIds) {
    try (ExecutorService executor =
        Executors.newFixedThreadPool(CONCURRENCY, Thread.ofVirtual().factory())) {
      awaitAll(newIds.stream().map(id -> executor.submit(() -> deduplicateOne(id, newIds))).toList());
    }
  }

  private void awaitAll(List<? extends Future<?>> futures) {
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Interrupted while waiting for a question-dedup task", e);
      } catch (ExecutionException e) {
        log.warn("A question-dedup task failed unexpectedly", e.getCause());
      }
    }
  }

  private void deduplicateOne(UUID questionId, Set<UUID> newIds) {
    Optional<Question> question = questionRepository.findById(questionId);
    if (question.isEmpty()) {
      return;
    }

    Optional<MatchResult> match = findExistingMatch(question.get(), newIds);
    if (match.isEmpty()) {
      countItem("no_match");
      return;
    }

    mergeInto(match.get(), question.get());
  }

  private record MatchResult(UUID targetId, float similarity) {

  }

  /**
   * Finds THE single highest-probability existing match for {@code question} — not the best of
   * several candidates that already clear some bar, but the top result itself, checked against
   * {@link #EXACT_MATCH_THRESHOLD} in code (not as a query-level filter): the ranked results are
   * walked past the question's own self-match, any other brand-new question from the same batch,
   * and any stale {@code vector_store} entry, and the very first survivor is that single best
   * match. If that match doesn't clear the threshold, nothing further down the ranked list could
   * either (similarity only decreases from there), so the question is left as its own standalone
   * entry rather than auto-merged — but a match that's close (≥ {@link #REVIEW_THRESHOLD}) without
   * being close enough to auto-merge is flagged for human review instead of silently dropped, since
   * two questions can cover the same topic while asking for genuinely different things (e.g. "list
   * the isolation levels" vs. "what anomaly does each prevent") — not confidently a duplicate, but
   * too close to ignore.
   */
  private Optional<MatchResult> findExistingMatch(Question question, Set<UUID> newIds) {
    List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
        .query(question.getContent()).topK(SEARCH_TOP_K).build());

    for (Document doc : hits) {
      Optional<UUID> candidateId = VectorStoreIds.parse(doc.getId());
      if (candidateId.isEmpty() || candidateId.get().equals(question.getId())) {
        continue;
      }
      UUID id = candidateId.get();
      if (newIds.contains(id) || !questionRepository.existsById(id)) {
        continue;
      }
      float similarity = doc.getScore() == null ? 0f : doc.getScore().floatValue();
      if (similarity >= EXACT_MATCH_THRESHOLD) {
        return Optional.of(new MatchResult(id, similarity));
      }
      if (similarity >= REVIEW_THRESHOLD) {
        log.info("Question {} closely matches existing question {} (similarity={}) — below the "
                + "auto-merge threshold, flagging as a candidate for human review via "
                + "/merge/candidates rather than merging automatically",
            question.getId(), id, similarity);
        countItem("review_candidate");
      }
      return Optional.empty();
    }
    return Optional.empty();
  }

  private void mergeInto(MatchResult match, Question newQuestion) {
    Optional<Question> target = questionRepository.findById(match.targetId());
    if (target.isEmpty()) {
      return;
    }

    Optional<QuestionMergeResult> merged = rephrase(target.get().getContent(),
        newQuestion.getContent());
    if (merged.isEmpty()) {
      log.warn("Skipping auto-merge of question {} into {}: rephrase call failed",
          newQuestion.getId(), match.targetId());
      countItem("failed");
      return;
    }

    target.get().setContent(merged.get().mergedQuestion());
    target.get().setContentHash(ContentHash.sha256(merged.get().mergedQuestion()));
    questionRepository.save(target.get());

    mergeService.merge(match.targetId(), newQuestion.getId(), match.similarity(),
        "auto-merged during ingestion");

    reclassify(match.targetId());
    countItem("merged");
    log.info("Auto-merged question {} into {} (similarity={}): {}", newQuestion.getId(),
        match.targetId(), match.similarity(), merged.get().rationale());
  }

  private void reclassify(UUID targetId) {
    questionRepository.findByIdWithSkills(targetId).ifPresent(target -> {
      Optional<QuestionLevelClassification> classification = classifier.classify(target);
      if (classification.isEmpty()) {
        return;
      }
      target.setLevel(classification.get().level());
      questionRepository.save(target);
    });
  }

  private Optional<QuestionMergeResult> rephrase(String existingContent, String newContent) {
    String userText = "Existing question:\n" + existingContent + "\n\nNew question:\n" + newContent;

    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      ResponseEntity<ChatResponse, QuestionMergeResult> result = chatClient.prompt()
          .system(spec -> spec.text(mergeSystemPromptResource))
          .user(userText)
          .call()
          .responseEntity(QuestionMergeResult.class);

      tokenMetrics.record(JOB, result.response());
      QuestionMergeResult mergeResult = result.entity();
      if (mergeResult == null || mergeResult.mergedQuestion() == null
          || mergeResult.mergedQuestion().isBlank()) {
        return Optional.empty();
      }
      return Optional.of(mergeResult);
    } catch (RuntimeException e) {
      log.warn("Question-merge rephrase call failed", e);
      return Optional.empty();
    } finally {
      sample.stop(meterRegistry.timer("ingest.job.call.duration", "job", JOB, "step", "rephrase"));
    }
  }

  private void countItem(String outcome) {
    meterRegistry.counter("ingest.job.items", "job", JOB, "outcome", outcome).increment();
  }
}
