package io.github.posseidon.knowledgebase.it.interview.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionMapperTest {

  private final QuestionMapper mapper = new QuestionMapper();

  @Test
  void mapsAllFieldsIncludingSkillsAndAnswers() {
    Question question = new Question("What is polymorphism?", "hash");
    question.setId(java.util.UUID.randomUUID());
    question.setExternalId("ext-1");
    question.setRequiresImpl(true);
    question.setLanguage("java");
    question.setFrequency(3);

    Skill skill = new Skill("OOP", "OOP", null, null, null);
    skill.setId(java.util.UUID.randomUUID());
    question.setSkills(Set.of(skill));

    Answer answer = new Answer(question, "Ability to take many forms", "hash2", "human");
    answer.setId(java.util.UUID.randomUUID());
    question.setAnswers(Set.of(answer));

    QuestionView view = mapper.toView(question);

    assertThat(view.id()).isEqualTo(question.getId());
    assertThat(view.externalId()).isEqualTo("ext-1");
    assertThat(view.content()).isEqualTo("What is polymorphism?");
    assertThat(view.requiresImpl()).isTrue();
    assertThat(view.language()).isEqualTo("java");
    assertThat(view.frequency()).isEqualTo(3);
    assertThat(view.skills()).hasSize(1);
    assertThat(view.skills().get(0).name()).isEqualTo("OOP");
    assertThat(view.answers()).hasSize(1);
    assertThat(view.answers().get(0).content()).isEqualTo("Ability to take many forms");
  }

  @Test
  void mapsQuestionWithNoSkillsOrAnswers() {
    Question question = new Question("content", "hash");
    question.setId(java.util.UUID.randomUUID());

    QuestionView view = mapper.toView(question);

    assertThat(view.skills()).isEmpty();
    assertThat(view.answers()).isEmpty();
  }
}
