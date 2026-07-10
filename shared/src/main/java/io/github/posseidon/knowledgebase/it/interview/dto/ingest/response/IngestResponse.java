package io.github.posseidon.knowledgebase.it.interview.dto.ingest.response;

public record IngestResponse(
    int questionsCreated,
    int questionsUpdated,
    int answersAdded
) {

}
