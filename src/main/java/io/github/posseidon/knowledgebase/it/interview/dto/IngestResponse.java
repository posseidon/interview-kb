package io.github.posseidon.knowledgebase.it.interview.dto;

public record IngestResponse(
    int topicsCreated,
    int topicsUpdated,
    int tagsCreated,
    int tagsUpdated,
    int questionsCreated,
    int questionsUpdated,
    int answersAdded
) {
}
