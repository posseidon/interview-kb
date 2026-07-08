package io.github.posseidon.knowledgebase.it.interview.dto;

import java.util.UUID;

public record InterviewIngestResponse(UUID id, String projectCode, int questionsLinked) {}
