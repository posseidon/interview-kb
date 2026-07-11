package io.github.posseidon.knowledgebase.it.interview.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.dto.ask.AskResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AskControllerTest {

  @Test
  void delegatesQueryToServiceAndReturnsOk() {
    AskService askService = mock(AskService.class);
    AskController controller = new AskController(askService);
    AskResponse response = new AskResponse("answer", List.of());
    when(askService.ask("what is java?")).thenReturn(response);

    ResponseEntity<AskResponse> result =
        controller.ask(new AskController.AskRequest("what is java?"));

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isSameAs(response);
  }
}
