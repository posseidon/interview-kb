package io.github.posseidon.knowledgebase.it.interview.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewTest {

  @Test
  void noArgConstructorLeavesEmptyQuestionSet() {
    Interview interview = new Interview();

    assertThat(interview.getQuestions()).isEmpty();
  }

  @Test
  void gettersAndSettersRoundTrip() {
    Interview interview = new Interview();
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 9);
    Instant now = Instant.now();
    Question question = new Question("content", "hash");

    interview.setId(id);
    interview.setProjectCode("PROJ-1");
    interview.setDate(date);
    interview.setFeedback("feedback");
    interview.setUpskillingPlan("plan");
    interview.setDecision(Decision.MAYBE);
    interview.setCreatedAt(now);
    interview.setQuestions(Set.of(question));

    assertThat(interview.getId()).isEqualTo(id);
    assertThat(interview.getProjectCode()).isEqualTo("PROJ-1");
    assertThat(interview.getDate()).isEqualTo(date);
    assertThat(interview.getFeedback()).isEqualTo("feedback");
    assertThat(interview.getUpskillingPlan()).isEqualTo("plan");
    assertThat(interview.getDecision()).isEqualTo(Decision.MAYBE);
    assertThat(interview.getCreatedAt()).isEqualTo(now);
    assertThat(interview.getQuestions()).containsExactly(question);
  }
}
