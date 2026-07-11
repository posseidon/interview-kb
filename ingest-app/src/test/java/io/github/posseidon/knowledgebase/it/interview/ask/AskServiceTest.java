package io.github.posseidon.knowledgebase.it.interview.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.ask.AskResponse;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class AskServiceTest {

  private VectorStore vectorStore;
  private QuestionRepository questionRepository;
  private AskService askService;

  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    questionRepository = mock(QuestionRepository.class);
    askService = new AskService(vectorStore, questionRepository, new QuestionMapper());
  }

  @Test
  void returnsSourcesOrderedByScoreDescending() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Document lowScore = Document.builder().id(id1.toString()).text("q1").score(0.5).build();
    Document highScore = Document.builder().id(id2.toString()).text("q2").score(0.9).build();
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(lowScore, highScore));

    Question q1 = new Question("q1 content", "hash1");
    q1.setId(id1);
    Question q2 = new Question("q2 content", "hash2");
    q2.setId(id2);
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of(q1, q2));

    AskResponse response = askService.ask("some query");

    assertThat(response.sources()).hasSize(2);
    assertThat(response.sources().get(0).id()).isEqualTo(id2);
    assertThat(response.sources().get(1).id()).isEqualTo(id1);
  }

  @Test
  void skipsDocumentsWithNonUuidIdInsteadOfFailing() {
    Document badDoc = Document.builder().id("not-a-uuid").text("q").score(0.9).build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(badDoc));
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of());

    AskResponse response = askService.ask("some query");

    assertThat(response.sources()).isEmpty();
  }

  @Test
  void returnsStubAnswerText() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of());

    AskResponse response = askService.ask("some query");

    assertThat(response.answer()).contains("Stub");
  }
}
