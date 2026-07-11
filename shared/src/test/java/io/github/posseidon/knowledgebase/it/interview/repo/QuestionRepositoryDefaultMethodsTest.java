package io.github.posseidon.knowledgebase.it.interview.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionRepositoryDefaultMethodsTest {

  private final QuestionRepository repository = mock(QuestionRepository.class,
      CALLS_REAL_METHODS);

  @Test
  void indexByExternalIdKeysQuestionsByTheirExternalId() {
    Question q1 = new Question("content-1", "hash-1");
    q1.setExternalId("ext-1");
    Question q2 = new Question("content-2", "hash-2");
    q2.setExternalId("ext-2");
    when(repository.findAllByExternalIdIn(anyCollection())).thenReturn(List.of(q1, q2));

    var index = repository.indexByExternalId(Set.of("ext-1", "ext-2"));

    assertThat(index).hasSize(2);
    assertThat(index.get("ext-1")).isSameAs(q1);
    assertThat(index.get("ext-2")).isSameAs(q2);
  }

  @Test
  void indexByExternalIdReturnsEmptyMapWhenNoMatches() {
    when(repository.findAllByExternalIdIn(anyCollection())).thenReturn(List.of());

    assertThat(repository.indexByExternalId(Set.of("missing"))).isEmpty();
  }

  @Test
  void indexByContentHashKeysQuestionsByTheirContentHash() {
    Question q1 = new Question("content-1", "hash-1");
    when(repository.findAllByContentHashIn(anyCollection())).thenReturn(List.of(q1));

    var index = repository.indexByContentHash(Set.of("hash-1"));

    assertThat(index).containsEntry("hash-1", q1);
  }

  @Test
  void indexByContentHashReturnsEmptyMapWhenNoMatches() {
    when(repository.findAllByContentHashIn(anyCollection())).thenReturn(List.of());

    assertThat(repository.indexByContentHash(Set.of("missing"))).isEmpty();
  }
}
