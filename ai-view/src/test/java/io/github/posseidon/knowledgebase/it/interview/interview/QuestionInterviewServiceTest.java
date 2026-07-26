package io.github.posseidon.knowledgebase.it.interview.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class QuestionInterviewServiceTest {

  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.PromptSystemSpec systemSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private InterviewGenerationStore store;
  private QuestionInterviewService service;

  private static Question questionWith(String content) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    return question;
  }

  private static void awaitResult(InterviewGenerationStore store, String key)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (store.result(key).isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    ChatClient chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    systemSpec = mock(ChatClient.PromptSystemSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    Resource promptResource = new ClassPathResource("prompts/interview-system-prompt.st");

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(any(Consumer.class))).thenAnswer(invocation -> {
      Consumer<ChatClient.PromptSystemSpec> consumer = invocation.getArgument(0);
      consumer.accept(systemSpec);
      return requestSpec;
    });
    when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);

    store = new InterviewGenerationStore();
    service = new QuestionInterviewService(chatClient, store, promptResource);
  }

  @Test
  void generateReturnsEmptyListWithoutCallingModelWhenNoSourceQuestions() {
    List<InterviewQuestion> result = service.generate("key", List.of(), "Senior", 5, List.of());

    assertThat(result).isEmpty();
    verify(requestSpec, never()).user(anyString());
  }

  @Test
  void loadsPromptFromTheConfiguredClasspathResource() {
    when(callResponseSpec.content()).thenReturn("no matching question blocks");

    service.generate("key", List.of(questionWith("What is a partition?")), "Senior", 3,
        List.of());

    verify(systemSpec).text(any(Resource.class));
  }

  @Test
  void userTextIncludesSourceQuestionIdAndContent() {
    when(callResponseSpec.content()).thenReturn("no matching question blocks");
    Question question = questionWith("What is a partition?");

    service.generate("key", List.of(question), "Senior", 3, List.of());

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userCaptor.capture());
    assertThat(userCaptor.getValue())
        .contains("Generate 3 interview questions for a Senior candidate")
        .contains("[" + question.getId() + "] What is a partition?")
        .contains("None.");
  }

  @Test
  void userTextListsAlreadyAskedQuestionsWhenPresent() {
    when(callResponseSpec.content()).thenReturn("no matching question blocks");

    service.generate("key", List.of(questionWith("Q")), "Senior", 3, List.of("Prior question?"));

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userCaptor.capture());
    assertThat(userCaptor.getValue()).contains("- Prior question?");
  }

  @Test
  void parsesValidQuestionBlockReferencingARealSourceId() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        Follow-ups:
        - Why does partitioning affect ordering?
        """.formatted(question.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(question), "Senior", 1,
        List.of());

    assertThat(result).hasSize(1);
    InterviewQuestion iq = result.get(0);
    assertThat(iq.question()).isEqualTo("What is a partition?");
    assertThat(iq.type()).isEqualTo("answer");
    assertThat(iq.sources()).containsExactly(question.getId().toString());
    assertThat(iq.followUps()).containsExactly("Why does partitioning affect ordering?");
  }

  @Test
  void parsesFollowUpsWrittenWithAsteriskOrBulletDotMarkersInsteadOfDashes() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        Follow-ups:
        * Why does partitioning affect ordering?
        • How would you choose a partition count?
        """.formatted(question.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(question), "Senior", 1,
        List.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).followUps()).containsExactly(
        "Why does partitioning affect ordering?", "How would you choose a partition count?");
  }

  @Test
  void parsesAFollowUpInlinedOnTheFollowUpsHeaderLine() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        Follow-ups: - Why does partitioning affect ordering?
        """.formatted(question.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(question), "Senior", 1,
        List.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).followUps())
        .containsExactly("Why does partitioning affect ordering?");
  }

  @Test
  void parsesASourceIdWrappedInSquareBracketsAsTheModelSometimesEmitsThem() {
    Question q1 = questionWith("Explain synchronized methods vs blocks.");
    Question q2 = questionWith("How does ConcurrentHashMap work?");
    String md = """
        ### Question 1
        Q: Implement a double-ended queue (deque) that stores strings.
        Type: coding
        Sources: [%s]
        Follow-ups:

        ### Question 2
        Q: Describe how ConcurrentHashMap changed between Java 7 and Java 8+.
        Type: answer
        Sources: [%s]
        Follow-ups:
        - What are the failure modes under extreme contention in the Java 7 implementation of ConcurrentHashMap?
        """.formatted(q1.getId(), q2.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(q1, q2), "Senior", 2,
        List.of());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).sources()).containsExactly(q1.getId().toString());
    assertThat(result.get(0).followUps()).isEmpty();
    assertThat(result.get(1).sources()).containsExactly(q2.getId().toString());
  }

  @Test
  void discardsBlocksThatCiteMoreThanOneSourceIdEvenWhenBracketed() {
    List<String> ids = List.of(
        "0653cdd4-34fd-46b0-9446-170bcc641052", "74a1f9ba-698a-442b-b638-f59a7a2f656e",
        "64a8fbcd-badc-4d40-9f09-d4741c3e470e", "163d04f1-1b60-4d27-a14c-08607923ff3d");
    List<Question> questions = ids.stream().map(id -> {
      Question q = questionWith("Question for " + id);
      q.setId(UUID.fromString(id));
      return q;
    }).toList();
    String md = """
        ### Question 1
        Q: How do you ensure thread safety in a Java trading engine that uses multiple threads to process orders?
        Type: answer
        Sources: [0653cdd4-34fd-46b0-9446-170bcc641052], [74a1f9ba-698a-442b-b638-f59a7a2f656e]
        Follow-ups:

        ### Question 2
        Q: Describe how ConcurrentHashMap changed between Java 7 and Java 8+.
        Type: answer
        Sources: [64a8fbcd-badc-4d40-9f09-d4741c3e470e]
        Follow-ups:

        ### Question 3
        Q: How does NUMA architecture affect a Java trading application's latency?
        Type: answer
        Sources: [163d04f1-1b60-4d27-a14c-08607923ff3d]
        Follow-ups:
        """;
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", questions, "Senior", 3, List.of());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).sources()).containsExactly(ids.get(2));
    assertThat(result.get(1).sources()).containsExactly(ids.get(3));
  }

  @Test
  void parsesMultipleQuestionBlocksInOneResponse() {
    Question q1 = questionWith("What is a partition?");
    Question q2 = questionWith("Implement a producer");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        
        ### Question 2
        Q: Implement a producer
        Type: coding
        Sources: %s
        """.formatted(q1.getId(), q2.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(q1, q2), "Senior", 2,
        List.of());

    assertThat(result).extracting(InterviewQuestion::question)
        .containsExactly("What is a partition?", "Implement a producer");
    assertThat(result).extracting(InterviewQuestion::type).containsExactly("answer", "coding");
  }

  @Test
  void discardsQuestionCitingTwoUnrelatedSources() {
    // Regression test: skill tags alone can't catch this, since both "Atomic" and
    // "Comparable vs Comparator" are commonly tagged with nothing more specific than the
    // broad "Java" skill — the fix is a hard "exactly one source" rule instead.
    Question atomic = questionWith("What is Atomic? Why and when is it useful?");
    Question comparableVsComparator =
        questionWith("What is the difference between Comparable and Comparator? When to use?");
    String md = """
        ### Question 1
        Q: Write a Java code snippet that demonstrates the use of Atomic variables.
        Type: coding
        Sources: %s, %s
        """.formatted(atomic.getId(), comparableVsComparator.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key",
        List.of(atomic, comparableVsComparator), "Senior", 1, List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void discardsQuestionCitingASourceIdNotInTheInputSet() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        """.formatted(UUID.randomUUID());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(question), "Senior", 1,
        List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void discardsQuestionWithNoSources() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        """;
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(question), "Senior", 1,
        List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void discardsQuestionWithAnUnrecognizedType() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: essay
        Sources: %s
        """.formatted(question.getId());
    when(callResponseSpec.content()).thenReturn(md);

    List<InterviewQuestion> result = service.generate("key", List.of(question), "Senior", 1,
        List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void reportsProgressThroughEachStageOfGeneration() {
    Question question = questionWith("What is a partition?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        """.formatted(question.getId());
    when(callResponseSpec.content()).thenReturn(md);

    service.generate("key-progress", List.of(question), "Senior", 1, List.of());

    assertThat(store.currentStep("key-progress"))
        .contains("Validating the answer and creating interview questions…");
  }

  @Test
  void throwsWhenTheModelReturnsAnEmptyAnswer() {
    Question question = questionWith("What is a partition?");
    when(callResponseSpec.content()).thenReturn("   ");

    assertThatThrownBy(
        () -> service.generate("key-empty", List.of(question), "Senior", 1, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Empty LLM response.");
    assertThat(store.currentStep("key-empty")).contains("The model has answered.");
  }

  @Test
  void storesGeneratedInterviewQuestionsAndClearsPendingStepViaGenerateAsync()
      throws InterruptedException {
    Question q1 = questionWith("What is a partition?");
    Question q2 = questionWith("What is a consumer group?");
    String md = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        
        ### Question 2
        Q: What is a consumer group?
        Type: coding
        Sources: %s
        """.formatted(q1.getId(), q2.getId());
    when(callResponseSpec.content()).thenReturn(md);

    service.generateAsync("key-1", List.of(q1, q2), "Senior", 2);
    awaitResult(store, "key-1");

    List<InterviewQuestion> result = store.result("key-1").orElseThrow();
    assertThat(result).extracting(InterviewQuestion::question)
        .containsExactly("What is a partition?", "What is a consumer group?");
    assertThat(store.currentStep("key-1")).isEmpty();
  }

  @Test
  void secondGenerationExcludesQuestionsFromTheFirst() throws InterruptedException {
    Question question = questionWith("What is a partition?");
    String firstMd = """
        ### Question 1
        Q: What is a partition?
        Type: answer
        Sources: %s
        """.formatted(question.getId());
    String secondMd = """
        ### Question 1
        Q: What is a consumer group?
        Type: answer
        Sources: %s
        """.formatted(question.getId());
    when(callResponseSpec.content()).thenReturn(firstMd).thenReturn(secondMd);

    service.generateAsync("key-2", List.of(question), "Senior", 1);
    awaitResult(store, "key-2");

    service.generateAsync("key-2", List.of(question), "Senior", 1);
    long deadline = System.currentTimeMillis() + 2000;
    while (store.askedQuestions("key-2").size() < 2 && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userCaptor.capture());
    assertThat(userCaptor.getAllValues().get(1)).contains("- What is a partition?");
  }
}
