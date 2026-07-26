package io.github.posseidon.knowledgebase.it.interview.quiz;

import java.util.List;

public record QuizQuestion(
    String question,
    List<String> options,
    int correctOptionIndex,
    String explanation) {

}
