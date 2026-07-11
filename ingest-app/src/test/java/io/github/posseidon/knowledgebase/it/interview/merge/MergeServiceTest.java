package io.github.posseidon.knowledgebase.it.interview.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.MergeLogRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class MergeServiceTest {

  private QuestionRepository questionRepository;
  private MergeLogRepository mergeLogRepository;
  private VectorStore vectorStore;
  private MergeService mergeService;

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    mergeLogRepository = mock(MergeLogRepository.class);
    vectorStore = mock(VectorStore.class);
    mergeService = new MergeService(questionRepository, mergeLogRepository, vectorStore,
        new ObjectMapper());
  }

  private static Question question(String content, String hash) {
    Question q = new Question(content, hash);
    q.setId(UUID.randomUUID());
    return q;
  }

  @Test
  void findCandidatesExcludesSelfMatch() {
    Question q = question("content", "hash");
    when(questionRepository.findAll()).thenReturn(List.of(q));
    Document selfDoc = Document.builder().id(q.getId().toString()).text("content").score(1.0)
        .build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(selfDoc));
    when(questionRepository.findExistingIds(anyCollection())).thenReturn(Set.of());

    List<MergeService.MergeCandidate> candidates = mergeService.findCandidates(0.7f);

    assertThat(candidates).isEmpty();
  }

  @Test
  void findCandidatesSkipsNonUuidDocumentIds() {
    Question q = question("content", "hash");
    when(questionRepository.findAll()).thenReturn(List.of(q));
    Document badDoc = Document.builder().id("not-a-uuid").text("other").score(0.9).build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(badDoc));
    when(questionRepository.findExistingIds(anyCollection())).thenReturn(Set.of());

    List<MergeService.MergeCandidate> candidates = mergeService.findCandidates(0.7f);

    assertThat(candidates).isEmpty();
  }

  @Test
  void findCandidatesFiltersOutStaleVectorStoreEntries() {
    Question q = question("content", "hash");
    UUID staleTargetId = UUID.randomUUID();
    when(questionRepository.findAll()).thenReturn(List.of(q));
    Document staleDoc = Document.builder().id(staleTargetId.toString()).text("other").score(0.9)
        .build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(staleDoc));
    when(questionRepository.findExistingIds(anyCollection())).thenReturn(Set.of());

    List<MergeService.MergeCandidate> candidates = mergeService.findCandidates(0.7f);

    assertThat(candidates).isEmpty();
  }

  @Test
  void findCandidatesReturnsValidMatches() {
    Question q = question("content", "hash");
    UUID targetId = UUID.randomUUID();
    when(questionRepository.findAll()).thenReturn(List.of(q));
    Document match = Document.builder().id(targetId.toString()).text("other").score(0.85).build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(match));
    when(questionRepository.findExistingIds(anyCollection())).thenReturn(Set.of(targetId));

    List<MergeService.MergeCandidate> candidates = mergeService.findCandidates(0.7f);

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).sourceId()).isEqualTo(q.getId());
    assertThat(candidates.get(0).targetId()).isEqualTo(targetId);
    assertThat(candidates.get(0).similarity()).isEqualTo(0.85f);
  }

  @Test
  void mergeCombinesAnswersAndSkillsAndDeletesSource() {
    Question target = question("target content", "hash-t");
    Question source = question("source content", "hash-s");
    Answer sourceAnswer = new Answer(source, "answer", "answer-hash", "human");
    source.setAnswers(Set.of(sourceAnswer));
    source.setFrequency(2);
    target.setFrequency(3);

    when(questionRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(questionRepository.findById(source.getId())).thenReturn(Optional.of(source));

    mergeService.merge(target.getId(), source.getId());

    assertThat(target.getAnswers()).contains(sourceAnswer);
    assertThat(sourceAnswer.getQuestion()).isSameAs(target);
    assertThat(target.getFrequency()).isEqualTo(5);
    verify(mergeLogRepository, times(1)).save(any());
    verify(questionRepository, times(1)).save(target);
    verify(vectorStore, times(1)).delete(List.of(source.getId().toString()));
    verify(vectorStore, times(1)).add(anyList());
    verify(questionRepository, times(1)).deleteById(source.getId());
  }

  @Test
  void mergeThrowsWhenTargetNotFound() {
    UUID targetId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    when(questionRepository.findById(targetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mergeService.merge(targetId, sourceId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Target question not found");

    verify(mergeLogRepository, never()).save(any());
  }

  @Test
  void mergeThrowsWhenSourceNotFound() {
    Question target = question("target content", "hash-t");
    UUID sourceId = UUID.randomUUID();
    when(questionRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(questionRepository.findById(sourceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mergeService.merge(target.getId(), sourceId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Source question not found");
  }
}
