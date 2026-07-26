package io.github.posseidon.knowledgebase.it.interview.quiz;

import io.github.posseidon.knowledgebase.it.interview.web.GenerationStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Tracks per-question quiz generation state, keyed by an opaque caller-supplied key (session id +
 * question id). Same async/poll shape as {@code chat.ChatProgressStore}, but also holds the
 * finished {@link Quiz} once generation completes, and a running history of previously-asked
 * question texts for that key — so a repeat "Generate Quiz" click can be steered away from
 * questions already shown, reinforcing learning through variety rather than repetition.
 *
 * <p>Generation happens in two model calls (first question, then the rest as a batch — see
 * {@code QuestionQuizService}), so {@link #partialQuestions}/{@link #appendQuestions} expose
 * questions as they're generated, ahead of {@link #complete}, letting a poller show the first
 * question well before the whole quiz is ready.
 */
@Component
public class QuizGenerationStore {

  private static final String DEFAULT_STEP = "Estimating quiz size…";

  private final GenerationStore<Quiz> delegate = new GenerationStore<>(DEFAULT_STEP,
      quiz -> quiz.questions().stream().map(QuizQuestion::question).toList());

  private final Map<String, List<QuizQuestion>> partialQuestionsByKey = new ConcurrentHashMap<>();
  private final Map<String, Integer> targetCountByKey = new ConcurrentHashMap<>();

  public void begin(String key) {
    partialQuestionsByKey.remove(key);
    targetCountByKey.remove(key);
    delegate.begin(key);
  }

  public void step(String key, String description) {
    delegate.step(key, description);
  }

  public void targetCount(String key, int count) {
    targetCountByKey.put(key, count);
  }

  public int targetCount(String key) {
    return targetCountByKey.getOrDefault(key, 0);
  }

  /**
   * Makes newly-generated questions visible to pollers immediately, ahead of {@link #complete},
   * so the quiz UI can let the user start answering while the rest of the quiz is still being
   * generated in the background.
   */
  public void appendQuestions(String key, List<QuizQuestion> newQuestions) {
    if (newQuestions.isEmpty()) {
      return;
    }
    partialQuestionsByKey.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
        .addAll(newQuestions);
  }

  public List<QuizQuestion> partialQuestions(String key) {
    return partialQuestionsByKey.getOrDefault(key, List.of());
  }

  public void complete(String key, Quiz quiz) {
    delegate.complete(key, quiz);
    // Deliberately NOT clearing partialQuestionsByKey/targetCountByKey here: the final poll
    // response (the one where pending flips to false) still needs to report the complete
    // question set, not an empty one. They're cleared at the start of the NEXT generation
    // instead, via begin().
  }

  public void clearPending(String key) {
    delegate.clearPending(key);
  }

  public Optional<String> currentStep(String key) {
    return delegate.currentStep(key);
  }

  public Optional<Quiz> result(String key) {
    return delegate.result(key);
  }

  public List<String> askedQuestions(String key) {
    return delegate.askedQuestions(key);
  }
}
