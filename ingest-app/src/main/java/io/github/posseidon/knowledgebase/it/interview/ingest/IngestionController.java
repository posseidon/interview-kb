package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionListRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.QuestionIngestResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {

    private final IngestionService ingestionService;
    private final AiIngestService aiIngestService;

    public IngestionController(IngestionService ingestionService, AiIngestService aiIngestService) {
        this.ingestionService = ingestionService;
        this.aiIngestService = aiIngestService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        return ResponseEntity.ok(ingestionService.ingest(request));
    }

    @PostMapping("/ingest/questions")
    public ResponseEntity<QuestionIngestResponse> ingestQuestions(@RequestBody QuestionListRequest request) {
        return ResponseEntity.ok(aiIngestService.ingestWithMarkdown(request.questions()));
    }
}
