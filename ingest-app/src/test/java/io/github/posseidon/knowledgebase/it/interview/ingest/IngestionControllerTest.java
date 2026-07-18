package io.github.posseidon.knowledgebase.it.interview.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IngestionControllerTest {

  private IngestionService ingestionService;
  private QuestionContentService questionContentService;
  private IngestionController controller;

  @BeforeEach
  void setUp() {
    ingestionService = mock(IngestionService.class);
    questionContentService = mock(QuestionContentService.class);
    controller = new IngestionController(ingestionService, questionContentService);
  }

  @Test
  void delegatesToServiceAndReturnsOk() {
    IngestRequest request = new IngestRequest(List.of());
    IngestResponse response = new IngestResponse(1, 0, 0);
    when(ingestionService.ingest(request)).thenReturn(response);

    ResponseEntity<IngestResponse> result = controller.ingest(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void overwriteQuestionContentDelegatesAndReturnsNoContent() {
    UUID questionId = UUID.randomUUID();

    ResponseEntity<Void> result = controller.overwriteQuestionContent(questionId, "new content");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(questionContentService).overwriteContent(questionId, "new content");
  }

  @Test
  void addAnswerDelegatesAndReturnsNoContent() {
    UUID questionId = UUID.randomUUID();

    ResponseEntity<Void> result = controller.addAnswer(questionId, "an answer");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(questionContentService).addAnswer(questionId, "an answer");
  }

  @Test
  void overwriteAnswerDelegatesAndReturnsNoContent() {
    UUID questionId = UUID.randomUUID();
    UUID answerId = UUID.randomUUID();

    ResponseEntity<Void> result = controller.overwriteAnswer(questionId, answerId, "updated");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(questionContentService).overwriteAnswer(answerId, "updated");
  }
}
