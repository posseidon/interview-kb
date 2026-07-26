package io.github.posseidon.knowledgebase.it.interview.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.classification.QuestionLevelClassification;
import io.github.posseidon.knowledgebase.it.interview.classification.QuestionLevelClassifier;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.merge.MergeService;
import io.github.posseidon.knowledgebase.it.interview.metrics.LlmTokenMetrics;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class QuestionDeduplicationServiceTest {

  private QuestionRepository questionRepository;
  private VectorStore vectorStore;
  private MergeService mergeService;
  private QuestionLevelClassifier classifier;
  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.PromptSystemSpec systemSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private SimpleMeterRegistry meterRegistry;
  private QuestionDeduplicationService service;

  private static Question questionWith(String content) {
    Question question = new Question(content, ContentHash.sha256(content));
    question.setId(UUID.randomUUID());
    return question;
  }

  private static Document hit(UUID id, double score) {
    return Document.builder().id(id.toString()).text("x").score(score).build();
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    vectorStore = mock(VectorStore.class);
    mergeService = mock(MergeService.class);
    classifier = mock(QuestionLevelClassifier.class);
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    systemSpec = mock(ChatClient.PromptSystemSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(any(Consumer.class))).thenAnswer(invocation -> {
      Consumer<ChatClient.PromptSystemSpec> consumer = invocation.getArgument(0);
      consumer.accept(systemSpec);
      return requestSpec;
    });
    when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);

    meterRegistry = new SimpleMeterRegistry();
    Resource promptResource = new ClassPathResource("prompts/question-merge-system.st");
    service = new QuestionDeduplicationService(questionRepository, vectorStore, mergeService,
        classifier, chatClient, meterRegistry, new LlmTokenMetrics(meterRegistry), promptResource);
  }

  @Test
  void noOpForEmptyOrNullIdList() {
    service.deduplicateAsync(List.of());
    service.deduplicateAsync(null);

    verifyNoInteractions(questionRepository, vectorStore, mergeService, classifier, chatClient);
  }

  @Test
  void leavesQuestionUntouchedWhenNoMatchIsFound() {
    Question question = questionWith("brand new question");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    service.deduplicateAsync(List.of(question.getId()));

    verify(vectorStore, timeout(2000)).similaritySearch(any(SearchRequest.class));
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
    verifyNoInteractions(chatClient);
  }

  @Test
  void ignoresSelfMatchInSimilaritySearchResults() {
    Question question = questionWith("brand new question");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(question.getId(), 0.99)));

    service.deduplicateAsync(List.of(question.getId()));

    verify(vectorStore, timeout(2000)).similaritySearch(any(SearchRequest.class));
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
  }

  @Test
  void ignoresMatchesThatAreAlsoBrandNewInTheSameBatch() {
    Question question = questionWith("brand new question");
    Question otherNewQuestion = questionWith("another new question");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    when(questionRepository.findById(otherNewQuestion.getId()))
        .thenReturn(Optional.of(otherNewQuestion));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(otherNewQuestion.getId(), 0.99)));

    service.deduplicateAsync(List.of(question.getId(), otherNewQuestion.getId()));

    verify(vectorStore, timeout(2000).times(2)).similaritySearch(any(SearchRequest.class));
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
  }

  @Test
  void doesNotMergeWhenTheSingleBestMatchIsBelowTheSimilarityThreshold() {
    Question question = questionWith("brand new question");
    Question weaklyRelatedExisting = questionWith("a loosely related existing question");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    when(questionRepository.existsById(weaklyRelatedExisting.getId())).thenReturn(true);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(weaklyRelatedExisting.getId(), 0.62)));

    service.deduplicateAsync(List.of(question.getId()));

    verify(vectorStore, timeout(2000)).similaritySearch(any(SearchRequest.class));
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
    verifyNoInteractions(chatClient);
  }

  @Test
  void flagsAReviewCandidateWhenTheBestMatchIsInTheMiddleBand() throws InterruptedException {
    Question question = questionWith("What are the isolation levels in a transaction?");
    Question closelyRelatedExisting =
        questionWith("Isolation levels: what anomaly does each prevent?");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    when(questionRepository.existsById(closelyRelatedExisting.getId())).thenReturn(true);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(closelyRelatedExisting.getId(), 0.85)));

    service.deduplicateAsync(List.of(question.getId()));

    verify(vectorStore, timeout(2000)).similaritySearch(any(SearchRequest.class));
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
    verifyNoInteractions(chatClient);
    // Give the async metric increment (right after the verified similaritySearch call, same
    // thread) a moment to land before reading the registry.
    Thread.sleep(50);
    assertThat(meterRegistry.get("ingest.job.items")
        .tag("job", "question_dedup").tag("outcome", "review_candidate")
        .counter().count()).isEqualTo(1);
  }

  @Test
  void ignoresStaleVectorStoreEntries() {
    Question question = questionWith("brand new question");
    UUID staleId = UUID.randomUUID();
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    when(questionRepository.existsById(staleId)).thenReturn(false);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(staleId, 0.99)));

    service.deduplicateAsync(List.of(question.getId()));

    verify(questionRepository, timeout(2000)).existsById(staleId);
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
  }

  @Test
  void mergesIntoExistingMatchUsingRephrasedContentAndReclassifies() {
    Question newQuestion = questionWith("What's the deal with thread safety?");
    Question target = questionWith("How do you achieve thread safety in Java?");
    Question reclassifyTarget = questionWith("merged and reclassified content");
    reclassifyTarget.setId(target.getId());

    when(questionRepository.findById(newQuestion.getId())).thenReturn(Optional.of(newQuestion));
    when(questionRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(questionRepository.existsById(target.getId())).thenReturn(true);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(target.getId(), 0.97)));
    when(callResponseSpec.responseEntity(QuestionMergeResult.class))
        .thenReturn(new ResponseEntity<>(null,
            new QuestionMergeResult("How do you ensure thread safety in Java?", "")));
    when(questionRepository.findByIdWithSkills(target.getId()))
        .thenReturn(Optional.of(reclassifyTarget));
    when(classifier.classify(reclassifyTarget)).thenReturn(Optional.of(
        new QuestionLevelClassification(SkillLevel.ADVANCED, "concurrency depth")));

    service.deduplicateAsync(List.of(newQuestion.getId()));

    verify(mergeService, timeout(2000)).merge(target.getId(), newQuestion.getId(), 0.97f,
        "auto-merged during ingestion");
    assertThat(target.getContent()).isEqualTo("How do you ensure thread safety in Java?");
    assertThat(target.getContentHash())
        .isEqualTo(ContentHash.sha256("How do you ensure thread safety in Java?"));
    verify(questionRepository).save(target);
    assertThat(reclassifyTarget.getLevel()).isEqualTo(SkillLevel.ADVANCED);
    verify(questionRepository).save(reclassifyTarget);
  }

  @Test
  void fallsBackToNoMergeWhenRephraseCallFails() {
    Question newQuestion = questionWith("What's the deal with thread safety?");
    Question target = questionWith("How do you achieve thread safety in Java?");
    when(questionRepository.findById(newQuestion.getId())).thenReturn(Optional.of(newQuestion));
    when(questionRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(questionRepository.existsById(target.getId())).thenReturn(true);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(target.getId(), 0.97)));
    when(callResponseSpec.responseEntity(QuestionMergeResult.class))
        .thenThrow(new RuntimeException("model unavailable"));

    service.deduplicateAsync(List.of(newQuestion.getId()));

    verify(chatClient, timeout(2000)).prompt();
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
    assertThat(target.getContent()).isEqualTo("How do you achieve thread safety in Java?");
  }

  @Test
  void doesNothingWhenTheNewQuestionNoLongerExists() {
    UUID questionId = UUID.randomUUID();
    when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

    service.deduplicateAsync(List.of(questionId));

    verify(questionRepository, timeout(2000)).findById(questionId);
    verifyNoInteractions(vectorStore, mergeService, chatClient);
  }

  @Test
  void skipsMergeWhenTargetNoLongerExistsByTheTimeItsProcessed() {
    Question newQuestion = questionWith("What's the deal with thread safety?");
    UUID targetId = UUID.randomUUID();
    when(questionRepository.findById(newQuestion.getId())).thenReturn(Optional.of(newQuestion));
    when(questionRepository.existsById(targetId)).thenReturn(true);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(hit(targetId, 0.97)));
    when(questionRepository.findById(targetId)).thenReturn(Optional.empty());

    service.deduplicateAsync(List.of(newQuestion.getId()));

    verify(questionRepository, timeout(2000)).findById(targetId);
    verifyNoInteractions(chatClient);
    verify(mergeService, never()).merge(any(), any(), anyFloat(), anyString());
  }
}
