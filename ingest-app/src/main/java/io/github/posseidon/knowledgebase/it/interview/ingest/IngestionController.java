package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {

  private final IngestionService ingestionService;
  private final QuestionContentService questionContentService;

  public IngestionController(IngestionService ingestionService,
      QuestionContentService questionContentService) {
    this.ingestionService = ingestionService;
    this.questionContentService = questionContentService;
  }

  @PostMapping("/ingest")
  public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
    return ResponseEntity.ok(ingestionService.ingest(request));
  }

  @PatchMapping(value = "/ingest/question/{questionId}", consumes = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<Void> overwriteQuestionContent(@PathVariable UUID questionId,
      @RequestBody String content) {
    questionContentService.overwriteContent(questionId, content);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/ingest/question/{questionId}", consumes = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<Void> addAnswer(@PathVariable UUID questionId,
      @RequestBody String content) {
    questionContentService.addAnswer(questionId, content);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping(value = "/ingest/answers/{answerId}",
      consumes = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<Void> overwriteAnswer(@PathVariable UUID answerId, @RequestBody String content) {
    questionContentService.overwriteAnswer(answerId, content);
    return ResponseEntity.noContent().build();
  }
}
