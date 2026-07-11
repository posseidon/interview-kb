package io.github.posseidon.knowledgebase.it.interview.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IngestionControllerTest {

  @Test
  void delegatesToServiceAndReturnsOk() {
    IngestionService ingestionService = mock(IngestionService.class);
    IngestionController controller = new IngestionController(ingestionService);
    IngestRequest request = new IngestRequest(List.of());
    IngestResponse response = new IngestResponse(1, 0, 0);
    when(ingestionService.ingest(request)).thenReturn(response);

    ResponseEntity<IngestResponse> result = controller.ingest(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isSameAs(response);
  }
}
