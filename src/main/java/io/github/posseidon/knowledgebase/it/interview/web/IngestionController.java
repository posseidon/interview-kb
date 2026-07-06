package io.github.posseidon.knowledgebase.it.interview.web;

import io.github.posseidon.knowledgebase.it.interview.dto.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.IngestResponse;
import io.github.posseidon.knowledgebase.it.interview.ingest.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        IngestResponse response = ingestionService.ingest(request);
        return ResponseEntity.ok(response);
    }
}
