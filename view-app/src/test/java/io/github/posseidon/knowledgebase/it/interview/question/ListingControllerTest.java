package io.github.posseidon.knowledgebase.it.interview.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ListingControllerTest {

  private QuestionRepository questionRepository;
  private ListingController controller;

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    controller = new ListingController(questionRepository, new QuestionMapper());
  }

  private static Question question() {
    Question q = new Question("content", "hash");
    q.setId(UUID.randomUUID());
    return q;
  }

  @Test
  void getQuestionsReturnsMappedPage() {
    Question question = question();
    PageRequest page = PageRequest.of(0, 20);
    when(questionRepository.findFilteredBySkill(isNull(), eq("java"), any()))
        .thenReturn(new PageImpl<>(List.of(question), page, 1));

    ResponseEntity<Page<QuestionView>> response = controller.getQuestions(null, "java", page);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getTotalElements()).isEqualTo(1);
  }

  @Test
  void getQuestionReturnsOkWhenFound() {
    Question question = question();
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    var response = controller.getQuestion(question.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().id()).isEqualTo(question.getId());
  }

  @Test
  void getQuestionReturnsNotFoundWhenMissing() {
    UUID id = UUID.randomUUID();
    when(questionRepository.findById(id)).thenReturn(Optional.empty());

    var response = controller.getQuestion(id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getQuestionsBySkillMapsResults() {
    Question question = question();
    UUID skillId = UUID.randomUUID();
    when(questionRepository.findBySkillId(eq(skillId), any())).thenReturn(List.of(question));

    var response = controller.getQuestionsBySkill(skillId, PageRequest.of(0, 20));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
  }
}
