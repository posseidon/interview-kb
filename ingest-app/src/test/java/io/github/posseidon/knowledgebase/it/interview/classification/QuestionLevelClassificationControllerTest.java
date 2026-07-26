package io.github.posseidon.knowledgebase.it.interview.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class QuestionLevelClassificationControllerTest {

  private QuestionLevelClassificationService classificationService;
  private QuestionLevelClassificationProgress progress;
  private QuestionLevelClassificationController controller;

  @BeforeEach
  void setUp() {
    classificationService = mock(QuestionLevelClassificationService.class);
    progress = mock(QuestionLevelClassificationProgress.class);
    controller = new QuestionLevelClassificationController(classificationService, progress);
  }

  @Test
  void classifyReturnsAcceptedWhenStarted() {
    when(classificationService.classifyAllAsync()).thenReturn(true);

    ResponseEntity<Void> result = controller.classify();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void classifyReturnsConflictWhenAlreadyRunning() {
    when(classificationService.classifyAllAsync()).thenReturn(false);

    ResponseEntity<Void> result = controller.classify();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void statusReflectsProgressState() {
    when(progress.isRunning()).thenReturn(true);
    when(progress.total()).thenReturn(100L);
    when(progress.processed()).thenReturn(42L);
    when(progress.skipped()).thenReturn(5L);
    when(progress.failed()).thenReturn(2L);
    when(progress.error()).thenReturn(null);

    QuestionLevelClassificationStatusView status = controller.status();

    assertThat(status.running()).isTrue();
    assertThat(status.total()).isEqualTo(100L);
    assertThat(status.processed()).isEqualTo(42L);
    assertThat(status.skipped()).isEqualTo(5L);
    assertThat(status.failed()).isEqualTo(2L);
    assertThat(status.error()).isNull();
  }
}
