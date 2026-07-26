package io.github.posseidon.knowledgebase.it.interview.web;

/**
 * Shared response shape for background-generation polling endpoints (quiz, interview, ...).
 */
public record GenerationStatusView(boolean pending, String step) {

}
