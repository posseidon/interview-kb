package io.github.posseidon.knowledgebase.it.interview.quiz;

import java.util.List;

/**
 * Poll response for in-progress quiz generation: the questions generated so far — shown
 * immediately, progressively, one model call at a time — plus how many are ultimately expected,
 * so the UI can render "Question 1 of N" before all N exist yet.
 */
public record QuizStatusView(boolean pending, String step, List<QuizQuestion> questions,
    int targetCount) {

}
