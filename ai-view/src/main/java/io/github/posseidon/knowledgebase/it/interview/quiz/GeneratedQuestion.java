package io.github.posseidon.knowledgebase.it.interview.quiz;

import java.util.List;

public record GeneratedQuestion(
    String question,
    List<String> options,     // exactly 4
    String correctAnswer,     // MUST be verbatim-identical to one of `options`
    String explanation) {

}
