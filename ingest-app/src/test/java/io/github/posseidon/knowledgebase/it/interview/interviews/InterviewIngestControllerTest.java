package io.github.posseidon.knowledgebase.it.interview.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewIngestResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewIngestControllerTest {

  @Test
  void ingestDelegatesToService() {
    InterviewIngestService service = mock(InterviewIngestService.class);
    InterviewIngestController controller = new InterviewIngestController(service);
    InterviewDto dto = new InterviewDto("PROJ-1", LocalDate.now(), null, null,
        Decision.MAYBE, List.of(), List.of());
    InterviewIngestResponse response = new InterviewIngestResponse(UUID.randomUUID(), "PROJ-1", 0);
    when(service.ingest(dto)).thenReturn(response);

    assertThat(controller.ingest(dto)).isSameAs(response);
  }

  @Test
  void addQuestionsDelegatesToService() {
    InterviewIngestService service = mock(InterviewIngestService.class);
    InterviewIngestController controller = new InterviewIngestController(service);
    InterviewDto dto = new InterviewDto("PROJ-1", LocalDate.now(), null, null,
        Decision.MAYBE, List.of(), List.of());
    InterviewIngestResponse response = new InterviewIngestResponse(UUID.randomUUID(), "PROJ-1", 1);
    when(service.addQuestions(dto)).thenReturn(response);

    assertThat(controller.addQuestions(dto)).isSameAs(response);
  }
}
