package io.github.posseidon.knowledgebase.it.interview.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class QuestionEditServiceTest {

  private QuestionRepository questionRepository;
  private AnswerRepository answerRepository;
  private QuestionEditService service;

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    answerRepository = mock(AnswerRepository.class);
    service = new QuestionEditService(questionRepository, answerRepository);
  }

  @Test
  void updateQuestionContentIgnoresBlankContent() {
    UUID id = UUID.randomUUID();

    service.updateQuestionContent(id, "   ");

    verify(questionRepository, never()).findById(any());
  }

  @Test
  void updateQuestionContentThrowsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(questionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateQuestionContent(id, "new content"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void updateQuestionContentSkipsUpdateWhenHashUnchanged() {
    Question question = new Question("same content", ContentHash.sha256("same content"));
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    service.updateQuestionContent(question.getId(), "same content");

    assertThat(question.getContent()).isEqualTo("same content");
  }

  @Test
  void updateQuestionContentUpdatesWhenHashChanged() {
    Question question = new Question("old content", ContentHash.sha256("old content"));
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    service.updateQuestionContent(question.getId(), "  new content  ");

    assertThat(question.getContent()).isEqualTo("new content");
    assertThat(question.getContentHash()).isEqualTo(ContentHash.sha256("new content"));
  }

  @Test
  void addAnswerIgnoresBlankContent() {
    service.addAnswer(UUID.randomUUID(), null);

    verify(answerRepository, never()).save(any());
  }

  @Test
  void addAnswerSavesStrippedAnswer() {
    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    service.addAnswer(question.getId(), "  the answer  ");

    org.mockito.ArgumentCaptor<Answer> captor = org.mockito.ArgumentCaptor.forClass(Answer.class);
    verify(answerRepository).save(captor.capture());
    assertThat(captor.getValue().getContent()).isEqualTo("the answer");
    assertThat(captor.getValue().getSource()).isEqualTo("human");
  }

  @Test
  void updateAnswerIgnoresBlankContent() {
    service.updateAnswer(UUID.randomUUID(), "");

    verify(answerRepository, never()).findById(any());
  }

  @Test
  void updateAnswerThrowsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(answerRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateAnswer(id, "content"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void updateAnswerUpdatesContentAndHash() {
    Answer answer = new Answer(new Question("q", "h"), "old", "old-hash", "human");
    answer.setId(UUID.randomUUID());
    when(answerRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

    service.updateAnswer(answer.getId(), "  new answer  ");

    assertThat(answer.getContent()).isEqualTo("new answer");
    assertThat(answer.getContentHash()).isEqualTo(ContentHash.sha256("new answer"));
  }
}
