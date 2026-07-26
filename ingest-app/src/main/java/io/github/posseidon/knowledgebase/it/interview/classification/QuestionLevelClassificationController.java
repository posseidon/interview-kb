package io.github.posseidon.knowledgebase.it.interview.classification;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin trigger for reclassifying every question's {@code level} from its assigned skills'
 * descriptions and criteria — a one-off maintenance action, not part of normal ingestion. No auth
 * per this app's single-user model; call it manually (curl/Postman), not from the ingestion
 * pipeline.
 */
@RestController
@RequestMapping("/admin/question-level")
public class QuestionLevelClassificationController {

  private final QuestionLevelClassificationService classificationService;
  private final QuestionLevelClassificationProgress progress;

  public QuestionLevelClassificationController(
      QuestionLevelClassificationService classificationService,
      QuestionLevelClassificationProgress progress) {
    this.classificationService = classificationService;
    this.progress = progress;
  }

  @PostMapping("/classify")
  public ResponseEntity<Void> classify() {
    boolean started = classificationService.classifyAllAsync();
    return started ? ResponseEntity.accepted().build()
        : ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  @GetMapping("/classify/status")
  public QuestionLevelClassificationStatusView status() {
    return new QuestionLevelClassificationStatusView(progress.isRunning(), progress.total(),
        progress.processed(), progress.skipped(), progress.failed(), progress.error());
  }
}
