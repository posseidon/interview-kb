package io.github.posseidon.knowledgebase.it.interview.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionServiceTest {

  @Test
  void mapsUpsertResultToIngestResponse() {
    QuestionUpsertService questionUpsertService = mock(QuestionUpsertService.class);
    IngestionService service = new IngestionService(questionUpsertService);
    IngestRequest request = new IngestRequest(List.of());
    when(questionUpsertService.upsert(request.questions()))
        .thenReturn(new QuestionUpsertService.Result(List.of(), 2, 1, 3));

    IngestResponse response = service.ingest(request);

    assertThat(response.questionsCreated()).isEqualTo(2);
    assertThat(response.questionsUpdated()).isEqualTo(1);
    assertThat(response.answersAdded()).isEqualTo(3);
  }
}
