package io.github.posseidon.knowledgebase.it.interview.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MergeControllerTest {

  @Test
  void getCandidatesMapsServiceResultToDtos() {
    MergeService mergeService = mock(MergeService.class);
    MergeController controller = new MergeController(mergeService);
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    when(mergeService.findCandidates(0.7f))
        .thenReturn(List.of(new MergeService.MergeCandidate(sourceId, targetId, 0.9f)));

    ResponseEntity<List<MergeController.MergeCandidateDto>> response =
        controller.getCandidates(0.7f);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).sourceId()).isEqualTo(sourceId);
    assertThat(response.getBody().get(0).targetId()).isEqualTo(targetId);
    assertThat(response.getBody().get(0).similarity()).isEqualTo(0.9f);
  }

  @Test
  void mergeDelegatesToServiceAndReturnsNoContent() {
    MergeService mergeService = mock(MergeService.class);
    MergeController controller = new MergeController(mergeService);
    UUID targetId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    ResponseEntity<Void> response =
        controller.merge(new MergeController.MergeRequest(targetId, sourceId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(mergeService).merge(targetId, sourceId);
  }
}
