package io.github.posseidon.knowledgebase.it.interview.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerRepositoryDefaultMethodsTest {

  private final AnswerRepository repository = mock(AnswerRepository.class, CALLS_REAL_METHODS);

  @Test
  void groupsContentHashesByQuestionId() {
    Question q1 = new Question("content-1", "hash-1");
    q1.setId(UUID.randomUUID());
    Question q2 = new Question("content-2", "hash-2");
    q2.setId(UUID.randomUUID());

    Answer a1 = new Answer(q1, "answer-1", "answer-hash-1", "human");
    Answer a2 = new Answer(q1, "answer-2", "answer-hash-2", "human");
    Answer a3 = new Answer(q2, "answer-3", "answer-hash-3", "human");
    when(repository.findByQuestionIds(anyCollection())).thenReturn(List.of(a1, a2, a3));

    var grouped = repository.groupContentHashesByQuestionId(Set.of(q1.getId(), q2.getId()));

    assertThat(grouped.get(q1.getId())).containsExactlyInAnyOrder("answer-hash-1", "answer-hash-2");
    assertThat(grouped.get(q2.getId())).containsExactly("answer-hash-3");
  }

  @Test
  void returnsEmptyMapWhenNoAnswersFound() {
    when(repository.findByQuestionIds(anyCollection())).thenReturn(List.of());

    assertThat(repository.groupContentHashesByQuestionId(Set.of(UUID.randomUUID()))).isEmpty();
  }
}
