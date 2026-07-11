package io.github.posseidon.knowledgebase.it.interview.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class InterviewControllerTest {

  private static InterviewView view(UUID id) {
    return new InterviewView(id, "PROJ-1", "Jul 9, 2026", Decision.MAYBE, "maybe", "MAYBE",
        "", "", "", 0, List.of());
  }

  @Test
  void listAddsAllInterviewsToModel() {
    InterviewService service = mock(InterviewService.class);
    InterviewController controller = new InterviewController(service);
    when(service.findAll()).thenReturn(List.of(view(UUID.randomUUID())));

    Model model = new ExtendedModelMap();
    String result = controller.list(model);

    assertThat(result).isEqualTo("interviews/interviews");
    assertThat(model.getAttribute("interviews")).isNotNull();
  }

  @Test
  void detailAddsInterviewToModel() {
    InterviewService service = mock(InterviewService.class);
    InterviewController controller = new InterviewController(service);
    UUID id = UUID.randomUUID();
    when(service.findById(id)).thenReturn(Optional.of(view(id)));

    Model model = new ExtendedModelMap();
    String result = controller.detail(id, model);

    assertThat(result).isEqualTo("interviews/interview-detail");
    assertThat(model.getAttribute("interview")).isEqualTo(view(id));
  }

  @Test
  void detailThrowsNotFoundWhenMissing() {
    InterviewService service = mock(InterviewService.class);
    InterviewController controller = new InterviewController(service);
    UUID id = UUID.randomUUID();
    when(service.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.detail(id, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }
}
