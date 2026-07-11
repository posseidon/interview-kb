package io.github.posseidon.knowledgebase.it.interview.domain.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerTest {

  @Test
  void noArgConstructorDefaultsSourceToHuman() {
    Answer answer = new Answer();

    assertThat(answer.getSource()).isEqualTo("human");
  }

  @Test
  void allArgConstructorSetsFields() {
    Question question = new Question("content", "hash");

    Answer answer = new Answer(question, "answer content", "answer hash", "claude");

    assertThat(answer.getQuestion()).isSameAs(question);
    assertThat(answer.getContent()).isEqualTo("answer content");
    assertThat(answer.getContentHash()).isEqualTo("answer hash");
    assertThat(answer.getSource()).isEqualTo("claude");
  }

  @Test
  void gettersAndSettersRoundTrip() {
    Answer answer = new Answer();
    UUID id = UUID.randomUUID();
    Question question = new Question("content", "hash");
    Instant now = Instant.now();

    answer.setId(id);
    answer.setQuestion(question);
    answer.setContent("updated");
    answer.setContentHash("updated hash");
    answer.setSource("human");
    answer.setCreatedAt(now);

    assertThat(answer.getId()).isEqualTo(id);
    assertThat(answer.getQuestion()).isSameAs(question);
    assertThat(answer.getContent()).isEqualTo("updated");
    assertThat(answer.getContentHash()).isEqualTo("updated hash");
    assertThat(answer.getSource()).isEqualTo("human");
    assertThat(answer.getCreatedAt()).isEqualTo(now);
  }
}
