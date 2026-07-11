package io.github.posseidon.knowledgebase.it.interview.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionScopeTest {

  private static QuestionView view(boolean requiresImpl) {
    return new QuestionView(UUID.randomUUID(), null, "content", requiresImpl, "java", 1,
        List.of(), List.of());
  }

  @Test
  void codingScopeKeepsOnlyImplementationQuestions() {
    List<QuestionView> results = List.of(view(true), view(false));

    List<QuestionView> filtered = QuestionScope.filter(results, "coding");

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).requiresImpl()).isTrue();
  }

  @Test
  void theoryScopeKeepsOnlyNonImplementationQuestions() {
    List<QuestionView> results = List.of(view(true), view(false));

    List<QuestionView> filtered = QuestionScope.filter(results, "theory");

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).requiresImpl()).isFalse();
  }

  @Test
  void unknownOrNullScopeReturnsAllResults() {
    List<QuestionView> results = List.of(view(true), view(false));

    assertThat(QuestionScope.filter(results, null)).hasSize(2);
    assertThat(QuestionScope.filter(results, "all")).hasSize(2);
  }
}
