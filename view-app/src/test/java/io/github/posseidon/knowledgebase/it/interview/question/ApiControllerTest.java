package io.github.posseidon.knowledgebase.it.interview.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiControllerTest {

  private QuestionRepository questionRepository;
  private ApiController controller;

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    controller = new ApiController(questionRepository);
  }

  @Test
  void unansweredMapsQuestionsWithSkillNames() {
    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    Skill skill = new Skill("Java", "Java", null, null, null);
    question.setSkills(Set.of(skill));
    when(questionRepository.findUnansweredNonImpl()).thenReturn(List.of(question));

    List<ApiController.UnansweredQuestion> result = controller.unanswered();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).skills()).containsExactly("Java");
  }
}
