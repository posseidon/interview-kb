package io.github.posseidon.knowledgebase.it.interview.dto.ingest.response;

import java.util.List;
import java.util.UUID;

public record IngestResponse(
    int questionsCreated,
    int questionsUpdated,
    int answersAdded,
    List<UUID> questionIds
) {

}
