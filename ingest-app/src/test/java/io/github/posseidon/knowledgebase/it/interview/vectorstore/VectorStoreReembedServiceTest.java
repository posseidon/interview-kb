package io.github.posseidon.knowledgebase.it.interview.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class VectorStoreReembedServiceTest {

  private QuestionRepository questionRepository;
  private VectorStore vectorStore;
  private VectorStoreReembedProgress progress;
  private SimpleMeterRegistry meterRegistry;
  private VectorStoreReembedService service;

  private static Question questionWith(String content) {
    Question question = new Question(content, ContentHash.sha256(content));
    question.setId(UUID.randomUUID());
    return question;
  }

  private static void awaitCompletion(VectorStoreReembedProgress progress) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (progress.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    vectorStore = mock(VectorStore.class);
    meterRegistry = new SimpleMeterRegistry();
    progress = new VectorStoreReembedProgress(meterRegistry);
    service = new VectorStoreReembedService(questionRepository, vectorStore, progress, meterRegistry);
  }

  @Test
  void reembedsAllQuestionsInASingleBatch() throws InterruptedException {
    Question q1 = questionWith("Question one");
    Question q2 = questionWith("Question two");
    when(questionRepository.count()).thenReturn(2L);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(q1, q2), PageRequest.of(0, 50), 2));

    boolean started = service.reembedAllAsync();
    awaitCompletion(progress);

    assertThat(started).isTrue();
    verify(vectorStore).delete(List.of(q1.getId().toString(), q2.getId().toString()));
    verify(vectorStore).add(anyList());
    assertThat(progress.isRunning()).isFalse();
    assertThat(progress.total()).isEqualTo(2);
    assertThat(progress.processed()).isEqualTo(2);
    assertThat(progress.error()).isNull();
  }

  @Test
  void addsDocumentsBuiltFromEachQuestion() throws InterruptedException {
    Question q1 = questionWith("What is a partition?");
    when(questionRepository.count()).thenReturn(1L);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(q1), PageRequest.of(0, 50), 1));

    service.reembedAllAsync();
    awaitCompletion(progress);

    org.mockito.ArgumentCaptor<List<Document>> docsCaptor =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(docsCaptor.capture());
    assertThat(docsCaptor.getValue()).hasSize(1);
    assertThat(docsCaptor.getValue().get(0).getId()).isEqualTo(q1.getId().toString());
    assertThat(docsCaptor.getValue().get(0).getText()).isEqualTo("What is a partition?");
  }

  @Test
  void processesMultiplePagesUntilExhausted() throws InterruptedException {
    Question q1 = questionWith("q1");
    Question q2 = questionWith("q2");
    when(questionRepository.count()).thenReturn(2L);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(q1), PageRequest.of(0, 1), 2))
        .thenReturn(new PageImpl<>(List.of(q2), PageRequest.of(1, 1), 2));

    service.reembedAllAsync();
    awaitCompletion(progress);

    verify(questionRepository, times(2)).findAllWithSkills(any(Pageable.class));
    verify(vectorStore, times(2)).add(anyList());
    assertThat(progress.processed()).isEqualTo(2);
  }

  @Test
  void secondTriggerWhileRunningIsRejected() {
    when(questionRepository.count()).thenReturn(0L);
    progress.tryStart(); // simulate a run already in progress

    boolean started = service.reembedAllAsync();

    assertThat(started).isFalse();
  }

  @Test
  void doesNothingWhenThereAreNoQuestions() throws InterruptedException {
    when(questionRepository.count()).thenReturn(0L);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    service.reembedAllAsync();
    awaitCompletion(progress);

    verify(vectorStore, times(0)).delete(anyList());
    verify(vectorStore, times(0)).add(anyList());
    assertThat(progress.error()).isNull();
  }

  @Test
  void recordsEmbedBatchDurationTimer() throws InterruptedException {
    Question q1 = questionWith("q1");
    when(questionRepository.count()).thenReturn(1L);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(q1), PageRequest.of(0, 50), 1));

    service.reembedAllAsync();
    awaitCompletion(progress);

    assertThat(meterRegistry.get("ingest.job.call.duration")
        .tag("job", "vector_reembed").tag("step", "embedBatch").timer().count())
        .isEqualTo(1);
  }

  @Test
  void recordsFailureWhenVectorStoreThrows() throws InterruptedException {
    Question q1 = questionWith("q1");
    when(questionRepository.count()).thenReturn(1L);
    when(questionRepository.findAllWithSkills(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(q1), PageRequest.of(0, 50), 1));
    doThrow(new RuntimeException("Azure OpenAI unavailable")).when(vectorStore).delete(anyList());

    service.reembedAllAsync();
    awaitCompletion(progress);

    assertThat(progress.isRunning()).isFalse();
    assertThat(progress.error()).isEqualTo("Azure OpenAI unavailable");
  }
}
