package io.github.posseidon.knowledgebase.it.interview.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.dedup.QuestionDeduplicationService;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.AnswerDto;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.skill.SkillResolver;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;

class QuestionUpsertServiceTest {

  private SkillResolver skillResolver;
  private QuestionRepository questionRepository;
  private AnswerRepository answerRepository;
  private VectorStore vectorStore;
  private QuestionDeduplicationService deduplicationService;
  private QuestionUpsertService service;

  private static QuestionDto dto(String externalId, String content, List<AnswerDto> answers) {
    return new QuestionDto(externalId, content, false, "java", List.of(), answers, null);
  }

  @BeforeEach
  void setUp() {
    skillResolver = mock(SkillResolver.class);
    questionRepository = mock(QuestionRepository.class);
    answerRepository = mock(AnswerRepository.class);
    vectorStore = mock(VectorStore.class);
    deduplicationService = mock(QuestionDeduplicationService.class);
    service = new QuestionUpsertService(skillResolver, questionRepository, answerRepository,
        vectorStore, deduplicationService);

    when(skillResolver.resolve(anySet())).thenReturn(Map.of());
    when(questionRepository.indexByExternalId(anyCollection())).thenReturn(Map.of());
    when(questionRepository.indexByContentHash(anyCollection())).thenReturn(Map.of());
    // Mimics real JPA save(All) behavior: brand-new entities get a generated id assigned.
    when(questionRepository.saveAll(any())).thenAnswer(inv -> {
      List<Question> questions = inv.getArgument(0);
      questions.forEach(q -> {
        if (q.getId() == null) {
          q.setId(UUID.randomUUID());
        }
      });
      return questions;
    });
  }

  @Test
  void emptyOrNullListShortCircuits() {
    QuestionUpsertService.Result result = service.upsert(List.of());

    assertThat(result.questions()).isEmpty();
    assertThat(result.created()).isZero();
    verify(questionRepository, never()).saveAll(any());

    assertThat(service.upsert(null).questions()).isEmpty();
  }

  @Test
  void createsNewQuestionWhenNoMatchExists() {
    QuestionDto dto = dto("ext-1", "brand new content", List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    assertThat(result.questions()).hasSize(1);
    Question saved = result.questions().get(0);
    assertThat(saved.getContent()).isEqualTo("brand new content");
    assertThat(saved.getExternalId()).isEqualTo("ext-1");
    verify(vectorStore, never()).delete(anyList());
    verify(vectorStore).add(any());
  }

  @Test
  void newQuestionGetsLevelFromDto() {
    QuestionDto dto = new QuestionDto("ext-1", "brand new content", false, "java", List.of(),
        List.of(), SkillLevel.EXPERT);

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.questions().get(0).getLevel()).isEqualTo(SkillLevel.EXPERT);
  }

  @Test
  void newQuestionDefaultsToNoviceWhenDtoOmitsLevel() {
    QuestionDto dto = dto("ext-1", "brand new content", List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.questions().get(0).getLevel()).isEqualTo(SkillLevel.NOVICE);
  }

  @Test
  void matchesExistingByExternalIdAndSkipsVectorDeleteWhenContentUnchanged() {
    String content = "unchanged content";
    Question existing = new Question(content, ContentHash.sha256(content));
    existing.setId(UUID.randomUUID());
    existing.setExternalId("ext-1");
    when(questionRepository.indexByExternalId(anyCollection()))
        .thenReturn(Map.of("ext-1", existing));

    QuestionDto dto = dto("ext-1", content, List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.created()).isZero();
    assertThat(result.updated()).isEqualTo(1);
    verify(vectorStore, never()).delete(anyList());
    verify(vectorStore).add(any());
  }

  @Test
  void reingestingAnExistingQuestionDoesNotClobberItsAlreadyClassifiedLevel() {
    String content = "existing content";
    Question existing = new Question(content, ContentHash.sha256(content));
    existing.setId(UUID.randomUUID());
    existing.setExternalId("ext-1");
    existing.setLevel(SkillLevel.ADVANCED);
    when(questionRepository.indexByExternalId(anyCollection()))
        .thenReturn(Map.of("ext-1", existing));

    // Re-ingested payload doesn't carry a level, so the DTO defaults it to NOVICE.
    QuestionDto dto = dto("ext-1", content, List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.questions().get(0).getLevel()).isEqualTo(SkillLevel.ADVANCED);
  }

  @Test
  void matchesExistingByExternalIdAndDeletesStaleVectorWhenContentChanged() {
    Question existing = new Question("old content", ContentHash.sha256("old content"));
    existing.setId(UUID.randomUUID());
    existing.setExternalId("ext-1");
    when(questionRepository.indexByExternalId(anyCollection()))
        .thenReturn(Map.of("ext-1", existing));

    QuestionDto dto = dto("ext-1", "new content", List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(existing.getContent()).isEqualTo("new content");
    verify(vectorStore).delete(List.of(existing.getId().toString()));
    verify(vectorStore).add(any());
  }

  @Test
  void matchesExistingByContentHashWhenNoExternalIdMatch() {
    String content = "matched by hash";
    Question existing = new Question(content, ContentHash.sha256(content));
    existing.setId(UUID.randomUUID());
    when(questionRepository.indexByContentHash(anyCollection()))
        .thenReturn(Map.of(ContentHash.sha256(content), existing));

    QuestionDto dto = dto(null, content, List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.created()).isZero();
    assertThat(result.questions().get(0)).isSameAs(existing);
  }

  @Test
  void savesNewAnswersButSkipsBlankAndDuplicateOnes() {
    Question existing = new Question("content", ContentHash.sha256("content"));
    existing.setId(UUID.randomUUID());
    existing.setExternalId("ext-1");
    when(questionRepository.indexByExternalId(anyCollection()))
        .thenReturn(Map.of("ext-1", existing));

    String existingAnswerContent = "already there";
    String existingAnswerHash = ContentHash.sha256(existingAnswerContent);
    when(answerRepository.groupContentHashesByQuestionId(anyCollection()))
        .thenReturn(Map.of(existing.getId(), Set.of(existingAnswerHash)));

    List<AnswerDto> answers = List.of(
        new AnswerDto("human", existingAnswerContent), // duplicate, skip
        new AnswerDto(null, "  "), // blank, skip
        new AnswerDto(null, "brand new answer") // new, save
    );
    QuestionDto dto = dto("ext-1", "content", answers);

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    assertThat(result.answersAdded()).isEqualTo(1);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(answerRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(((io.github.posseidon.knowledgebase.it.interview.domain.question.Answer)
        captor.getValue().get(0)).getContent()).isEqualTo("brand new answer");
    assertThat(((io.github.posseidon.knowledgebase.it.interview.domain.question.Answer)
        captor.getValue().get(0)).getSource()).isEqualTo("human");
  }

  @Test
  void doesNotSaveAnswersWhenNoneQualify() {
    QuestionDto dto = dto("ext-1", "content", List.of());

    service.upsert(List.of(dto));

    verify(answerRepository, never()).saveAll(any());
  }

  @Test
  void resolvesSkillsAndFiltersOutUnresolvedNames() {
    Skill javaSkill = new Skill("Java", "Java", null, null, null);
    javaSkill.setId(UUID.randomUUID());
    when(skillResolver.resolve(anySet())).thenReturn(Map.of("Java", javaSkill));

    QuestionDto dto = new QuestionDto("ext-1", "content", false, "java",
        List.of("Java", "Unknown"), List.of(), null);

    QuestionUpsertService.Result result = service.upsert(List.of(dto));

    Question saved = result.questions().get(0);
    assertThat(saved.getSkills()).containsExactly(javaSkill);
  }

  @Test
  @SuppressWarnings("unchecked")
  void triggersDeduplicationForNewlyCreatedQuestionsOnly() {
    Question existing = new Question("existing content", ContentHash.sha256("existing content"));
    existing.setId(UUID.randomUUID());
    existing.setExternalId("ext-existing");
    when(questionRepository.indexByExternalId(anyCollection()))
        .thenReturn(Map.of("ext-existing", existing));

    QuestionDto updateDto = dto("ext-existing", "existing content", List.of());
    QuestionDto newDto = dto("ext-new", "brand new content", List.of());

    QuestionUpsertService.Result result = service.upsert(List.of(updateDto, newDto));

    ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
    verify(deduplicationService).deduplicateAsync(idsCaptor.capture());
    assertThat(idsCaptor.getValue()).doesNotContain(existing.getId());
    assertThat(result.createdQuestionIds()).isEqualTo(idsCaptor.getValue());
  }

  @Test
  void doesNotTriggerDeduplicationWhenNoQuestionsAreCreated() {
    Question existing = new Question("existing content", ContentHash.sha256("existing content"));
    existing.setId(UUID.randomUUID());
    existing.setExternalId("ext-existing");
    when(questionRepository.indexByExternalId(anyCollection()))
        .thenReturn(Map.of("ext-existing", existing));

    service.upsert(List.of(dto("ext-existing", "existing content", List.of())));

    verify(deduplicationService, never()).deduplicateAsync(any());
  }
}
