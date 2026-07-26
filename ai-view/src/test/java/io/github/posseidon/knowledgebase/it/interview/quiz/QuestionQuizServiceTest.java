package io.github.posseidon.knowledgebase.it.interview.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class QuestionQuizServiceTest {

  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.PromptSystemSpec systemSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private QuizGenerationStore store;
  private QuestionQuizService service;

  /**
   * A {@code GeneratedQuestion} whose correct answer is uniquely derivable from the question text,
   * so tests can assert the correct option survived {@code toStoredQuestion}'s shuffle without
   * depending on a fixed option index.
   */
  private static GeneratedQuestion generatedQuestion(String question) {
    String correct = "correct-" + question;
    return new GeneratedQuestion(question, List.of("a", "b", "c", correct), correct, "because");
  }

  private static List<GeneratedQuestion> generatedQuestions(String... questions) {
    return List.of(questions).stream()
        .map(QuestionQuizServiceTest::generatedQuestion)
        .toList();
  }

  private static void awaitResult(QuizGenerationStore store, String key)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (store.result(key).isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }

  /**
   * Stubs consecutive {@code .entity(...)} calls in order — one per model call the service makes
   * (first question, then the remaining batch, in that order). The service asks for a bare
   * {@code List<GeneratedQuestion>} (not an object-wrapped record) because that's the JSON shape
   * small local models reliably produce.
   */
  @SafeVarargs
  @SuppressWarnings("unchecked")
  private final void stubEntity(List<GeneratedQuestion>... responses) {
    OngoingStubbing<Object> stubbing = when(
        callResponseSpec.entity(any(ParameterizedTypeReference.class)));
    for (List<GeneratedQuestion> response : responses) {
      stubbing = stubbing.thenReturn(response);
    }
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    ChatClient quizChatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    systemSpec = mock(ChatClient.PromptSystemSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    Resource promptResource = new ClassPathResource("prompts/quiz-system-prompt.st");

    when(quizChatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(any(Consumer.class))).thenAnswer(invocation -> {
      Consumer<ChatClient.PromptSystemSpec> consumer = invocation.getArgument(0);
      consumer.accept(systemSpec);
      return requestSpec;
    });
    when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);

    store = new QuizGenerationStore();
    service = new QuestionQuizService(quizChatClient, store, promptResource);
  }

  @Test
  void loadsPromptFromTheConfiguredClasspathResource() throws InterruptedException {
    stubEntity(generatedQuestions("q1", "q2", "q3"));

    service.generateAsync("key-0", "What is X?", List.of("Short answer."));
    awaitResult(store, "key-0");

    verify(systemSpec).text(any(Resource.class));
  }

  @Test
  void firstCallRequestsExactlyOneQuestion() throws InterruptedException {
    stubEntity(generatedQuestions("q1"), generatedQuestions("q2", "q3"));

    service.generateAsync("key-1", "What is X?", List.of("Short answer."));
    awaitResult(store, "key-1");

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(0))
        .contains("Generate 1 NEW multiple-choice question ");
  }

  @Test
  void generatesMinimumThreeQuestionsForShortAnswers() throws InterruptedException {
    stubEntity(generatedQuestions("q1"), generatedQuestions("q2", "q3"));

    service.generateAsync("key-1b", "What is X?", List.of("Short answer."));
    awaitResult(store, "key-1b");

    // Target is 3 (the minimum); after the first question, the batch call asks for the other 2.
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(1)).contains("Generate 2 NEW multiple-choice questions");
  }

  @Test
  void scalesQuestionCountWithAnswerLength() throws InterruptedException {
    stubEntity(generatedQuestions("q1"), generatedQuestions("q2", "q3", "q4", "q5", "q6"));
    String longAnswer = "word ".repeat(700);

    service.generateAsync("key-2", "What is X?", List.of(longAnswer));
    awaitResult(store, "key-2");

    // Target is 6; after the first question, the batch call asks for the other 5.
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(1)).contains("Generate 5 NEW multiple-choice questions");
  }

  @Test
  void clampsQuestionCountToTenForVeryLongAnswers() throws InterruptedException {
    stubEntity(generatedQuestions("q1"),
        generatedQuestions("q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10"));
    String hugeAnswer = "word ".repeat(3000);

    service.generateAsync("key-3", "What is X?", List.of(hugeAnswer));
    awaitResult(store, "key-3");

    // Target clamps to 10; after the first question, the batch call asks for the other 9.
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(1)).contains("Generate 9 NEW multiple-choice questions");
  }

  @Test
  void storesGeneratedQuizAndClearsPendingStep() throws InterruptedException {
    stubEntity(generatedQuestions("q1"), generatedQuestions("q2"));

    service.generateAsync("key-4", "What is X?", List.of("Some answer content."));
    awaitResult(store, "key-4");

    Quiz quiz = store.result("key-4").orElseThrow();
    assertThat(quiz.questions()).extracting(QuizQuestion::question).containsExactly("q1", "q2");
    assertThat(quiz.questions()).allSatisfy(q -> {
      assertThat(q.options()).hasSize(4);
      assertThat(q.options().get(q.correctOptionIndex())).isEqualTo("correct-" + q.question());
      assertThat(q.explanation()).isEqualTo("because");
    });
    assertThat(store.currentStep("key-4")).isEmpty();
  }

  @Test
  void completesWithEmptyQuizWhenModelOutputCannotBeParsed() throws InterruptedException {
    when(callResponseSpec.entity(any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("malformed model output"));

    service.generateAsync("key-malformed", "What is X?", List.of("Some answer content."));
    awaitResult(store, "key-malformed");

    assertThat(store.result("key-malformed")).contains(new Quiz(List.of()));
    assertThat(store.currentStep("key-malformed")).isEmpty();
  }

  @Test
  void referenceContentIncludesQuestionAndAnswerText() throws InterruptedException {
    stubEntity(generatedQuestions("q1"));

    service.generateAsync("key-5", "What is a partition?", List.of("Unit of parallelism."));
    awaitResult(store, "key-5");

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(0))
        .contains("What is a partition?")
        .contains("Unit of parallelism.");
  }

  @Test
  void firstGenerationHasNoPriorQuestionsToAvoid() throws InterruptedException {
    stubEntity(generatedQuestions("q1"));

    service.generateAsync("key-6", "What is X?", List.of("Some content."));
    awaitResult(store, "key-6");

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(0)).contains("(none yet)");
  }

  @Test
  void secondGenerationAsksModelToAvoidPreviouslyAskedQuestions() throws InterruptedException {
    // Each generation's first call already satisfies the (minimum) target of 3, so no batch
    // follow-up call happens within a single generation — isolating the cross-generation
    // exclusion behavior this test targets.
    stubEntity(generatedQuestions("q1a", "q1b", "q1c"),
        generatedQuestions("q2a", "q2b", "q2c"));

    service.generateAsync("key-7", "Topic", List.of("content"));
    awaitResult(store, "key-7");

    service.generateAsync("key-7", "Topic", List.of("content"));
    long deadline = System.currentTimeMillis() + 2000;
    while (store.askedQuestions("key-7").size() < 6 && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(1)).contains("q1a");
  }

  @Test
  void firstQuestionIsVisibleBeforeGenerationCompletes() throws InterruptedException {
    CountDownLatch secondCallStarted = new CountDownLatch(1);
    CountDownLatch releaseSecondCall = new CountDownLatch(1);
    when(callResponseSpec.entity(any(ParameterizedTypeReference.class)))
        .thenReturn(generatedQuestions("q1"))
        .thenAnswer(invocation -> {
          secondCallStarted.countDown();
          releaseSecondCall.await();
          return generatedQuestions("q2", "q3");
        });

    service.generateAsync("key-partial", "What is X?", List.of("word ".repeat(300)));

    assertThat(secondCallStarted.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(store.partialQuestions("key-partial"))
        .extracting(QuizQuestion::question)
        .containsExactly("q1");
    assertThat(store.result("key-partial")).isEmpty();

    releaseSecondCall.countDown();
    awaitResult(store, "key-partial");

    assertThat(store.result("key-partial").orElseThrow().questions())
        .extracting(QuizQuestion::question)
        .containsExactly("q1", "q2", "q3");
    // Regression guard: the final poll response (the one where pending flips to false) must
    // still report the complete question set, not an emptied partial-questions map, or the quiz
    // widget gets stuck unable to advance past the first question.
    assertThat(store.partialQuestions("key-partial"))
        .extracting(QuizQuestion::question)
        .containsExactly("q1", "q2", "q3");
  }

  @Test
  void retriesFirstQuestionOnceBeforeFallingBackToFullBatch() throws InterruptedException {
    stubEntity(List.of(), generatedQuestions("q1"), generatedQuestions("q2", "q3"));

    service.generateAsync("key-retry", "What is X?", List.of("Short answer."));
    awaitResult(store, "key-retry");

    verify(requestSpec, times(3)).user(anyString());
    assertThat(store.result("key-retry").orElseThrow().questions())
        .extracting(QuizQuestion::question)
        .containsExactly("q1", "q2", "q3");
  }

  @Test
  void fallsBackToFullBatchWhenFirstQuestionFailsTwice() throws InterruptedException {
    stubEntity(List.of(), List.of(), generatedQuestions("q1", "q2", "q3"));

    service.generateAsync("key-fallback", "What is X?", List.of("Short answer."));
    awaitResult(store, "key-fallback");

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(3)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(2))
        .contains("Generate 3 NEW multiple-choice questions");
    assertThat(store.result("key-fallback").orElseThrow().questions())
        .extracting(QuizQuestion::question)
        .containsExactly("q1", "q2", "q3");
  }
}
