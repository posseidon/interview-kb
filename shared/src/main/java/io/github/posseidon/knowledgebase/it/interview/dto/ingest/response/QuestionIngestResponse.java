package io.github.posseidon.knowledgebase.it.interview.dto.ingest.response;

import java.util.List;

public record QuestionIngestResponse(List<String> skills, int questionCount) {}
