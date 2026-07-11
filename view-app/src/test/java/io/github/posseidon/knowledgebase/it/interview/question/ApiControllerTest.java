package io.github.posseidon.knowledgebase.it.interview.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiControllerTest {

  private QuestionRepository questionRepository;
  private AnswerRepository answerRepository;
  private ApiController controller;

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    answerRepository = mock(AnswerRepository.class);
    controller = new ApiController(questionRepository, answerRepository);
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

  @Test
  void addAnswerReturnsBadRequestWhenContentBlank() {
    UUID id = UUID.randomUUID();

    ResponseEntity<Void> response = controller.addAnswer(id,
        new ApiController.AddAnswerRequest("   ", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(questionRepository, never()).findById(id);
  }

  @Test
  void addAnswerThrowsNotFoundWhenQuestionMissing() {
    UUID id = UUID.randomUUID();
    when(questionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.addAnswer(id,
        new ApiController.AddAnswerRequest("content", null)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void addAnswerDefaultsSourceToClaudeWhenBlank() {
    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    ResponseEntity<Void> response = controller.addAnswer(question.getId(),
        new ApiController.AddAnswerRequest("  the answer  ", "  "));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    org.mockito.ArgumentCaptor<io.github.posseidon.knowledgebase.it.interview.domain.question.Answer> captor =
        org.mockito.ArgumentCaptor.forClass(
            io.github.posseidon.knowledgebase.it.interview.domain.question.Answer.class);
    verify(answerRepository).save(captor.capture());
    assertThat(captor.getValue().getContent()).isEqualTo("the answer");
    assertThat(captor.getValue().getSource()).isEqualTo("claude");
  }

  @Test
  void addAnswerKeepsExplicitSource() {
    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    controller.addAnswer(question.getId(),
        new ApiController.AddAnswerRequest("answer", "human"));

    org.mockito.ArgumentCaptor<io.github.posseidon.knowledgebase.it.interview.domain.question.Answer> captor =
        org.mockito.ArgumentCaptor.forClass(
            io.github.posseidon.knowledgebase.it.interview.domain.question.Answer.class);
    verify(answerRepository).save(captor.capture());
    assertThat(captor.getValue().getSource()).isEqualTo("human");
  }
}
