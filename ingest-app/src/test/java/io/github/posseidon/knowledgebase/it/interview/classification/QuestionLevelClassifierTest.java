package io.github.posseidon.knowledgebase.it.interview.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.metrics.LlmTokenMetrics;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class QuestionLevelClassifierTest {

  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.PromptSystemSpec systemSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private SimpleMeterRegistry meterRegistry;
  private QuestionLevelClassifier classifier;

  private static Question questionWith(String content, Skill... skills) {
    Question question = new Question(content, ContentHash.sha256(content));
    question.setId(UUID.randomUUID());
    question.setSkills(new HashSet<>(Set.of(skills)));
    return question;
  }

  private static Skill skillWith(String name, String description) {
    Skill skill = new Skill();
    skill.setName(name);
    skill.setDescription(description);
    skill.setNoviceCriteria("novice criteria for " + name);
    skill.setIntermediateCriteria("intermediate criteria for " + name);
    skill.setAdvancedCriteria("advanced criteria for " + name);
    skill.setExpertCriteria("expert criteria for " + name);
    return skill;
  }

  private static ChatResponse chatResponseWithUsage(int promptTokens, int completionTokens) {
    ChatResponseMetadata metadata = ChatResponseMetadata.builder()
        .usage(new DefaultUsage(promptTokens, completionTokens))
        .build();
    return new ChatResponse(List.of(), metadata);
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    systemSpec = mock(ChatClient.PromptSystemSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    meterRegistry = new SimpleMeterRegistry();

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(any(Consumer.class))).thenAnswer(invocation -> {
      Consumer<ChatClient.PromptSystemSpec> consumer = invocation.getArgument(0);
      consumer.accept(systemSpec);
      return requestSpec;
    });
    when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);

    Resource promptResource =
        new ClassPathResource("prompts/skill-level-classification-system.st");
    classifier = new QuestionLevelClassifier(chatClient, meterRegistry,
        new LlmTokenMetrics(meterRegistry), promptResource);
  }

  @Test
  void returnsEmptyWithoutCallingTheModelWhenQuestionHasNoSkills() {
    Question question = questionWith("Standalone question");

    Optional<QuestionLevelClassification> result = classifier.classify(question);

    assertThat(result).isEmpty();
    verifyNoInteractions(chatClient);
  }

  @Test
  void returnsTheModelsClassification() {
    Skill java = skillWith("Java", "Java description");
    Question question = questionWith("How to ensure thread-safety?", java);
    when(callResponseSpec.responseEntity(QuestionLevelClassification.class))
        .thenReturn(new ResponseEntity<>(chatResponseWithUsage(100, 20),
            new QuestionLevelClassification(SkillLevel.ADVANCED, "needs concurrency internals")));

    Optional<QuestionLevelClassification> result = classifier.classify(question);

    assertThat(result).isPresent();
    assertThat(result.get().level()).isEqualTo(SkillLevel.ADVANCED);
    assertThat(result.get().rationale()).isEqualTo("needs concurrency internals");
  }

  @Test
  void promptIncludesEverySkillAssignedToTheQuestion() {
    Skill java = skillWith("Java", "Java description");
    Skill concurrency = skillWith("Concurrency", "Concurrency description");
    Question question = questionWith("How to ensure thread-safety?", java, concurrency);
    when(callResponseSpec.responseEntity(QuestionLevelClassification.class))
        .thenReturn(new ResponseEntity<>(null,
            new QuestionLevelClassification(SkillLevel.EXPERT, "requires expert reasoning")));

    classifier.classify(question);

    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userPrompt.capture());
    assertThat(userPrompt.getValue())
        .contains("Java").contains("Java description")
        .contains("Concurrency").contains("Concurrency description")
        .contains("How to ensure thread-safety?");
  }

  @Test
  void returnsEmptyWhenModelCallFails() {
    Skill java = skillWith("Java", "Java description");
    Question question = questionWith("How to ensure thread-safety?", java);
    when(callResponseSpec.responseEntity(QuestionLevelClassification.class))
        .thenThrow(new RuntimeException("model unavailable"));

    Optional<QuestionLevelClassification> result = classifier.classify(question);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyWhenModelReturnsNoClassification() {
    Skill java = skillWith("Java", "Java description");
    Question question = questionWith("How to ensure thread-safety?", java);
    when(callResponseSpec.responseEntity(QuestionLevelClassification.class))
        .thenReturn(new ResponseEntity<>(null, null));

    Optional<QuestionLevelClassification> result = classifier.classify(question);

    assertThat(result).isEmpty();
  }

  @Test
  void recordsPromptAndCompletionTokenUsage() {
    Skill java = skillWith("Java", "Java description");
    Question question = questionWith("How to ensure thread-safety?", java);
    when(callResponseSpec.responseEntity(QuestionLevelClassification.class))
        .thenReturn(new ResponseEntity<>(chatResponseWithUsage(150, 30),
            new QuestionLevelClassification(SkillLevel.ADVANCED, "needs concurrency internals")));

    classifier.classify(question);

    assertThat(meterRegistry.get("ingest.job.tokens")
        .tag("job", "skill_level_classification").tag("type", "prompt")
        .counter().count()).isEqualTo(150);
    assertThat(meterRegistry.get("ingest.job.tokens")
        .tag("job", "skill_level_classification").tag("type", "completion")
        .counter().count()).isEqualTo(30);
    assertThat(meterRegistry.get("ingest.job.tokens")
        .tag("job", "skill_level_classification").tag("type", "total")
        .counter().count()).isEqualTo(180);
  }
}
