package io.github.posseidon.knowledgebase.it.interview.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class SemanticSearchServiceTest {

  private VectorStore vectorStore;
  private QuestionRepository questionRepository;
  private ChatClient quizChatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private SemanticSearchService service;

  private static Question questionWith(String content) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    return question;
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    questionRepository = mock(QuestionRepository.class);
    quizChatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    when(quizChatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
    service = new SemanticSearchService(vectorStore, questionRepository, quizChatClient);
  }

  @Test
  void preservesSimilarityRankedOrderRegardlessOfRepositoryReturnOrder() {
    Question first = questionWith("most relevant");
    Question second = questionWith("second most relevant");
    Document firstDoc = Document.builder().id(first.getId().toString()).text("x").build();
    Document secondDoc = Document.builder().id(second.getId().toString()).text("y").build();
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(firstDoc, secondDoc));
    // Repository returns them in the opposite order, as findAllById does not guarantee order.
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of(second, first));

    List<Question> result = service.search("query", 8, 0.4f);

    assertThat(result).containsExactly(first, second);
  }

  @Test
  void skipsNonUuidDocumentIds() {
    Document doc = Document.builder().id("not-a-uuid").text("x").build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
    when(questionRepository.findAllById(List.of())).thenReturn(List.of());

    List<Question> result = service.search("anything", 8, 0.4f);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoSimilarityHits() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    when(questionRepository.findAllById(List.of())).thenReturn(List.of());

    List<Question> result = service.search("anything", 8, 0.4f);

    assertThat(result).isEmpty();
  }

  @Test
  void dropsHitsWhoseQuestionNoLongerExistsInRelationalStore() {
    Question existing = questionWith("still around");
    UUID deletedId = UUID.randomUUID();
    Document existingDoc = Document.builder().id(existing.getId().toString()).text("x").build();
    Document deletedDoc = Document.builder().id(deletedId.toString()).text("y").build();
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(deletedDoc, existingDoc));
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of(existing));

    List<Question> result = service.search("query", 8, 0.4f);

    assertThat(result).containsExactly(existing);
  }

  @Test
  void expandingSearchEmbedsOriginalQueryPlusModelExpansion() {
    when(callResponseSpec.content())
        .thenReturn("async, synchronized, deadlock, CompletableFuture, threads");
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of());

    service.searchExpandingQuery("parallelism", 10);

    ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getQuery())
        .contains("parallelism")
        .contains("async, synchronized, deadlock, CompletableFuture, threads");
  }

  @Test
  void expandingSearchFallsBackToRawQueryWhenExpansionIsBlank() {
    when(callResponseSpec.content()).thenReturn("   ");
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of());

    service.searchExpandingQuery("parallelism", 10);

    ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getQuery()).isEqualTo("parallelism");
  }

  @Test
  void expandingSearchFallsBackToRawQueryWhenExpansionCallFails() {
    when(requestSpec.call()).thenThrow(new RuntimeException("model unavailable"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    when(questionRepository.findAllById(anyCollection())).thenReturn(List.of());

    List<Question> result = service.searchExpandingQuery("parallelism", 10);

    assertThat(result).isEmpty();
    ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getQuery()).isEqualTo("parallelism");
  }
}
