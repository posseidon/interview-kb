package io.github.posseidon.knowledgebase.it.interview.util;

import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;

import java.util.List;

/**
 * The "coding" / "theory" result filter shared by every question-search entry point.
 */
public final class QuestionScope {

    private QuestionScope() {}

    public static List<QuestionView> filter(List<QuestionView> results, String scope) {
        if ("coding".equals(scope)) return results.stream().filter(QuestionView::requiresImpl).toList();
        if ("theory".equals(scope)) return results.stream().filter(r -> !r.requiresImpl()).toList();
        return results;
    }
}
