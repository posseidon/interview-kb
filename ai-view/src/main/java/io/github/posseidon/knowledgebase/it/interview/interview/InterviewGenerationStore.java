package io.github.posseidon.knowledgebase.it.interview.interview;

import io.github.posseidon.knowledgebase.it.interview.web.GenerationStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tracks per-topic interview-question generation state, keyed by an opaque caller-supplied key
 * (session id + search query). Same async/poll shape as
 * {@link QuizGenerationStore quiz.QuizGenerationStore}: holds the pending step text while
 * generation is running, the finished list of {@link InterviewQuestion}s once it completes, and a
 * running history of previously-generated question texts for that key so a repeat "Generate
 * Interview Questions" click can be steered away from questions already shown.
 */
@Component
public class InterviewGenerationStore {

  private static final String DEFAULT_STEP = "Reading the topic…";

  private final GenerationStore<List<InterviewQuestion>> delegate = new GenerationStore<>(
      DEFAULT_STEP, questions -> questions.stream().map(InterviewQuestion::question).toList());

  public void begin(String key) {
    delegate.begin(key);
  }

  public void step(String key, String description) {
    delegate.step(key, description);
  }

  public void complete(String key, List<InterviewQuestion> interviewSet) {
    delegate.complete(key, interviewSet);
  }

  public void clearPending(String key) {
    delegate.clearPending(key);
  }

  public Optional<String> currentStep(String key) {
    return delegate.currentStep(key);
  }

  public Optional<List<InterviewQuestion>> result(String key) {
    return delegate.result(key);
  }

  public List<String> askedQuestions(String key) {
    return delegate.askedQuestions(key);
  }
}
