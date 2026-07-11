package io.github.posseidon.knowledgebase.it.interview.domain.question;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionTest {

  @Test
  void noArgConstructorLeavesDefaults() {
    Question question = new Question();

    assertThat(question.getId()).isNull();
    assertThat(question.isRequiresImpl()).isFalse();
    assertThat(question.getFrequency()).isEqualTo(1);
    assertThat(question.getSkills()).isEmpty();
    assertThat(question.getAnswers()).isEmpty();
  }

  @Test
  void gettersAndSettersRoundTrip() {
    Question question = new Question("content", "hash");
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    Skill skill = new Skill();
    Answer answer = new Answer();

    question.setId(id);
    question.setExternalId("ext-1");
    question.setContent("updated content");
    question.setContentHash("updated hash");
    question.setRequiresImpl(true);
    question.setLanguage("java");
    question.setFrequency(5);
    question.setLevel(SkillLevel.ADVANCED);
    question.setCreatedAt(now);
    question.setUpdatedAt(now);
    question.setSkills(Set.of(skill));
    question.setAnswers(Set.of(answer));

    assertThat(question.getId()).isEqualTo(id);
    assertThat(question.getExternalId()).isEqualTo("ext-1");
    assertThat(question.getContent()).isEqualTo("updated content");
    assertThat(question.getContentHash()).isEqualTo("updated hash");
    assertThat(question.isRequiresImpl()).isTrue();
    assertThat(question.getLanguage()).isEqualTo("java");
    assertThat(question.getFrequency()).isEqualTo(5);
    assertThat(question.getLevel()).isEqualTo(SkillLevel.ADVANCED);
    assertThat(question.getCreatedAt()).isEqualTo(now);
    assertThat(question.getUpdatedAt()).isEqualTo(now);
    assertThat(question.getSkills()).containsExactly(skill);
    assertThat(question.getAnswers()).containsExactly(answer);
  }
}
