package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.QuestionIngestResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiIngestService {

    private final IngestionService ingestionService;

    public AiIngestService(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public QuestionIngestResponse ingestWithMarkdown(List<QuestionDto> dtos) {
        List<String> skills = dtos.stream()
                .flatMap(q -> q.skills().stream())
                .distinct().sorted().toList();

        ingestionService.ingest(new IngestRequest(dtos));

        return new QuestionIngestResponse(skills, dtos.size());
    }
}
