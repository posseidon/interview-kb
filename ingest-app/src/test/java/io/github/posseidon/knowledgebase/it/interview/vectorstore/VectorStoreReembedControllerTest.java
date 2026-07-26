package io.github.posseidon.knowledgebase.it.interview.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class VectorStoreReembedControllerTest {

  private VectorStoreReembedService reembedService;
  private VectorStoreReembedProgress progress;
  private VectorStoreReembedController controller;

  @BeforeEach
  void setUp() {
    reembedService = mock(VectorStoreReembedService.class);
    progress = mock(VectorStoreReembedProgress.class);
    controller = new VectorStoreReembedController(reembedService, progress);
  }

  @Test
  void reembedReturnsAcceptedWhenStarted() {
    when(reembedService.reembedAllAsync()).thenReturn(true);

    ResponseEntity<Void> result = controller.reembed();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void reembedReturnsConflictWhenAlreadyRunning() {
    when(reembedService.reembedAllAsync()).thenReturn(false);

    ResponseEntity<Void> result = controller.reembed();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void statusReflectsProgressState() {
    when(progress.isRunning()).thenReturn(true);
    when(progress.total()).thenReturn(100L);
    when(progress.processed()).thenReturn(42L);
    when(progress.error()).thenReturn(null);

    VectorStoreReembedStatusView status = controller.status();

    assertThat(status.running()).isTrue();
    assertThat(status.total()).isEqualTo(100L);
    assertThat(status.processed()).isEqualTo(42L);
    assertThat(status.error()).isNull();
  }
}
