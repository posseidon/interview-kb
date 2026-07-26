package io.github.posseidon.knowledgebase.it.interview.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuizGenerationStoreTest {

  private static Quiz quizWith(String... questions) {
    return new Quiz(List.of(questions).stream()
        .map(q -> new QuizQuestion(q, List.of("a", "b", "c", "d"), 0, "because"))
        .toList());
  }

  @Test
  void beginSetsDefaultStepAndClearsAnyPreviousResult() {
    QuizGenerationStore store = new QuizGenerationStore();
    store.complete("key", quizWith("old question"));

    store.begin("key");

    assertThat(store.currentStep("key")).contains("Estimating quiz size…");
    assertThat(store.result("key")).isEmpty();
  }

  @Test
  void stepOverwritesCurrentStep() {
    QuizGenerationStore store = new QuizGenerationStore();
    store.begin("key");

    store.step("key", "Generating 5 quiz questions…");

    assertThat(store.currentStep("key")).contains("Generating 5 quiz questions…");
  }

  @Test
  void completeStoresResultAndClearsPendingStep() {
    QuizGenerationStore store = new QuizGenerationStore();
    store.begin("key");
    Quiz quiz = quizWith("What is a partition?");

    store.complete("key", quiz);

    assertThat(store.result("key")).contains(quiz);
    assertThat(store.currentStep("key")).isEmpty();
  }

  @Test
  void clearPendingRemovesStepWithoutTouchingResult() {
    QuizGenerationStore store = new QuizGenerationStore();
    store.begin("key");
    Quiz quiz = quizWith("q1");
    store.complete("key", quiz);

    store.clearPending("key");

    assertThat(store.currentStep("key")).isEmpty();
    assertThat(store.result("key")).contains(quiz);
  }

  @Test
  void tracksMultipleKeysIndependently() {
    QuizGenerationStore store = new QuizGenerationStore();
    store.begin("key-1");
    store.begin("key-2");
    Quiz quiz1 = quizWith("q1");
    store.complete("key-1", quiz1);

    assertThat(store.result("key-1")).contains(quiz1);
    assertThat(store.result("key-2")).isEmpty();
    assertThat(store.currentStep("key-2")).contains("Estimating quiz size…");
  }

  @Test
  void unknownKeyReportsNoStepAndNoResult() {
    QuizGenerationStore store = new QuizGenerationStore();

    assertThat(store.currentStep("unknown")).isEmpty();
    assertThat(store.result("unknown")).isEmpty();
    assertThat(store.askedQuestions("unknown")).isEmpty();
  }

  @Test
  void completeAccumulatesAskedQuestionHistoryAcrossGenerations() {
    QuizGenerationStore store = new QuizGenerationStore();
    store.begin("key");
    store.complete("key", quizWith("first question", "second question"));

    store.begin("key");
    store.complete("key", quizWith("third question"));

    assertThat(store.askedQuestions("key"))
        .containsExactly("first question", "second question", "third question");
  }
}
