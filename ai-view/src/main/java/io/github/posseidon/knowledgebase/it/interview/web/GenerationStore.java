package io.github.posseidon.knowledgebase.it.interview.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Tracks per-key async generation state: the pending step text while generation is running, the
 * finished result once it completes, and a running history of previously-generated question texts
 * for that key (via {@code historyExtractor}) — capped at {@link #MAX_HISTORY} — so a repeat
 * generation click can be steered away from questions already shown.
 */
public class GenerationStore<T> {

  private static final int MAX_HISTORY = 40;

  private final String defaultStep;
  private final Function<T, List<String>> historyExtractor;

  private final Map<String, String> pendingSteps = new ConcurrentHashMap<>();
  private final Map<String, T> results = new ConcurrentHashMap<>();
  private final Map<String, List<String>> askedQuestionsByKey = new ConcurrentHashMap<>();

  public GenerationStore(String defaultStep, Function<T, List<String>> historyExtractor) {
    this.defaultStep = defaultStep;
    this.historyExtractor = historyExtractor;
  }

  public void begin(String key) {
    results.remove(key);
    pendingSteps.put(key, defaultStep);
  }

  public void step(String key, String description) {
    pendingSteps.put(key, description);
  }

  public void complete(String key, T result) {
    pendingSteps.remove(key);
    results.put(key, result);
    if (result == null) {
      return;
    }
    List<String> history = askedQuestionsByKey.computeIfAbsent(key,
        k -> new CopyOnWriteArrayList<>());
    history.addAll(historyExtractor.apply(result));
    while (history.size() > MAX_HISTORY) {
      history.remove(0);
    }
  }

  public void clearPending(String key) {
    pendingSteps.remove(key);
  }

  public Optional<String> currentStep(String key) {
    return Optional.ofNullable(pendingSteps.get(key));
  }

  public Optional<T> result(String key) {
    return Optional.ofNullable(results.get(key));
  }

  public List<String> askedQuestions(String key) {
    return askedQuestionsByKey.getOrDefault(key, List.of());
  }
}
