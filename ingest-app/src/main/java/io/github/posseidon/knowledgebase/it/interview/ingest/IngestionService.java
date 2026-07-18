package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

  private final QuestionUpsertService questionUpsertService;

  public IngestionService(QuestionUpsertService questionUpsertService) {
    this.questionUpsertService = questionUpsertService;
  }

  public IngestResponse ingest(IngestRequest request) {
    QuestionUpsertService.Result result = questionUpsertService.upsert(request.questions());
    return new IngestResponse(result.created(), result.updated(), result.answersAdded(), result.questionIds());
  }
}
