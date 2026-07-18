package io.github.posseidon.knowledgebase.it.interview.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.server.ResponseStatusException;

class QuestionContentServiceTest {

  private QuestionRepository questionRepository;
  private AnswerRepository answerRepository;
  private VectorStore vectorStore;
  private QuestionContentService service;

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    answerRepository = mock(AnswerRepository.class);
    vectorStore = mock(VectorStore.class);
    service = new QuestionContentService(questionRepository, answerRepository, vectorStore);
  }

  @Test
  void overwriteContentRejectsBlankContent() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> service.overwriteContent(id, "   "))
        .isInstanceOf(ResponseStatusException.class);
    verify(questionRepository, never()).findById(any());
  }

  @Test
  void overwriteContentThrowsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(questionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.overwriteContent(id, "new content"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void overwriteContentSkipsUpdateWhenHashUnchanged() {
    Question question = new Question("same content", ContentHash.sha256("same content"));
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    service.overwriteContent(question.getId(), "same content");

    assertThat(question.getContent()).isEqualTo("same content");
    verify(vectorStore, never()).delete(anyList());
    verify(vectorStore, never()).add(any());
  }

  @Test
  void overwriteContentUpdatesQuestionAndResyncsVectorStore() {
    Question question = new Question("old content", ContentHash.sha256("old content"));
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    service.overwriteContent(question.getId(), "  new content  ");

    assertThat(question.getContent()).isEqualTo("new content");
    assertThat(question.getContentHash()).isEqualTo(ContentHash.sha256("new content"));
    verify(vectorStore).delete(List.of(question.getId().toString()));
    verify(vectorStore).add(any());
  }

  @Test
  void addAnswerRejectsBlankContent() {
    assertThatThrownBy(() -> service.addAnswer(UUID.randomUUID(), null))
        .isInstanceOf(ResponseStatusException.class);
    verify(answerRepository, never()).save(any());
  }

  @Test
  void addAnswerSavesStrippedHumanAnswer() {
    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    service.addAnswer(question.getId(), "  the answer  ");

    ArgumentCaptor<Answer> captor = ArgumentCaptor.forClass(Answer.class);
    verify(answerRepository).save(captor.capture());
    assertThat(captor.getValue().getContent()).isEqualTo("the answer");
    assertThat(captor.getValue().getSource()).isEqualTo("human");
  }

  @Test
  void overwriteAnswerRejectsBlankContent() {
    assertThatThrownBy(() -> service.overwriteAnswer(UUID.randomUUID(), ""))
        .isInstanceOf(ResponseStatusException.class);
    verify(answerRepository, never()).findById(any());
  }

  @Test
  void overwriteAnswerThrowsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(answerRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.overwriteAnswer(id, "content"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void overwriteAnswerUpdatesContentAndHash() {
    Answer answer = new Answer(new Question("q", "h"), "old", "old-hash", "human");
    answer.setId(UUID.randomUUID());
    when(answerRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

    service.overwriteAnswer(answer.getId(), "  new answer  ");

    assertThat(answer.getContent()).isEqualTo("new answer");
    assertThat(answer.getContentHash()).isEqualTo(ContentHash.sha256("new answer"));
  }
}
