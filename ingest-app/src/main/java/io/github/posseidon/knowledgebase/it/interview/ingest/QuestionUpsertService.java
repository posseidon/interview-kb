package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.AnswerDto;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.skill.SkillResolver;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single upsert-by-externalId/contentHash implementation shared by every ingestion entry point
 * (direct /ingest and inline questions nested in /interviews), so the dedup rules and
 * relational+vector-store sync stay in exactly one place.
 */
@Service
public class QuestionUpsertService {

  private final SkillResolver skillResolver;
  private final QuestionRepository questionRepository;
  private final AnswerRepository answerRepository;
  private final VectorStore vectorStore;

  public QuestionUpsertService(SkillResolver skillResolver,
      QuestionRepository questionRepository,
      AnswerRepository answerRepository,
      VectorStore vectorStore) {
    this.skillResolver = skillResolver;
    this.questionRepository = questionRepository;
    this.answerRepository = answerRepository;
    this.vectorStore = vectorStore;
  }

  @Transactional
  public Result upsert(List<QuestionDto> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return Result.empty();
    }

    Map<String, Skill> skillByName = resolveSkills(dtos);
    Map<String, Question> byExternalId = indexExistingByExternalId(dtos);
    Map<String, Question> byContentHash = indexExistingByContentHash(dtos);

    List<ResolvedQuestion> resolved = dtos.stream()
        .map(dto -> resolveQuestion(dto, byExternalId, byContentHash, skillByName))
        .toList();

    Map<UUID, Set<String>> existingAnswerHashes = loadExistingAnswerHashes(resolved);
    List<Question> saved = saveQuestions(resolved);
    int answersAdded = saveNewAnswers(dtos, saved, existingAnswerHashes);
    syncVectorStore(saved);

    int created = (int) resolved.stream().filter(ResolvedQuestion::created).count();
    return new Result(saved, created, resolved.size() - created, answersAdded);
  }

  private Map<String, Skill> resolveSkills(List<QuestionDto> dtos) {
    Set<String> allSkillNames = dtos.stream()
        .flatMap(q -> q.skills().stream()).collect(Collectors.toSet());
    return skillResolver.resolve(allSkillNames);
  }

  /**
   * Batch fetch — one query for every externalId in the batch instead of one per question.
   */
  private Map<String, Question> indexExistingByExternalId(List<QuestionDto> dtos) {
    Set<String> allExternalIds = dtos.stream()
        .map(QuestionDto::externalId).filter(Objects::nonNull).collect(Collectors.toSet());
    return questionRepository.indexByExternalId(allExternalIds);
  }

  /**
   * Batch fetch — one query for every content hash in the batch instead of one per question.
   */
  private Map<String, Question> indexExistingByContentHash(List<QuestionDto> dtos) {
    Set<String> allContentHashes = dtos.stream()
        .map(d -> ContentHash.sha256(d.content())).collect(Collectors.toSet());
    return questionRepository.indexByContentHash(allContentHashes);
  }

  /**
   * Matches one DTO to an existing question by externalId, falling back to contentHash, falling
   * back to creating a new one — then applies the DTO's fields onto it.
   */
  private ResolvedQuestion resolveQuestion(QuestionDto dto,
      Map<String, Question> byExternalId,
      Map<String, Question> byContentHash,
      Map<String, Skill> skillByName) {
    ResolvedQuestion match = findOrCreate(dto, byExternalId, byContentHash);
    applyDtoFields(match.question(), dto, skillByName);
    return match;
  }

  /**
   * Batch fetch — one query for every pre-existing question's answer hashes.
   */
  private Map<UUID, Set<String>> loadExistingAnswerHashes(List<ResolvedQuestion> resolved) {
    Set<UUID> preExistingIds = resolved.stream()
        .filter(r -> !r.created())
        .map(r -> r.question().getId())
        .collect(Collectors.toSet());
    if (preExistingIds.isEmpty()) {
      return Map.of();
    }
    return answerRepository.groupContentHashesByQuestionId(preExistingIds);
  }

  /**
   * Deletes stale vector-store entries for edited questions, then batch-saves all questions.
   */
  private List<Question> saveQuestions(List<ResolvedQuestion> resolved) {
    List<String> staleVectorIds = resolved.stream()
        .filter(ResolvedQuestion::vectorStale)
        .map(r -> r.question().getId().toString())
        .toList();
    if (!staleVectorIds.isEmpty()) {
      vectorStore.delete(staleVectorIds);
    }

    return questionRepository.saveAll(resolved.stream().map(ResolvedQuestion::question).toList());
  }

  /**
   * Relies on {@code saved} being in the same order as {@code dtos} (saveAll preserves input
   * order).
   */
  private int saveNewAnswers(List<QuestionDto> dtos, List<Question> saved,
      Map<UUID, Set<String>> existingAnswerHashes) {
    List<Answer> answersToSave = new ArrayList<>();
    for (int i = 0; i < dtos.size(); i++) {
      QuestionDto dto = dtos.get(i);
      Question question = saved.get(i);
      Set<String> existingHashes = existingAnswerHashes.getOrDefault(question.getId(), Set.of());
      for (AnswerDto answerDto : dto.answers()) {
        if (answerDto.content() == null || answerDto.content().isBlank()) {
          continue;
        }
        String answerHash = ContentHash.sha256(answerDto.content());
        if (!existingHashes.contains(answerHash)) {
          answersToSave.add(new Answer(question, answerDto.content(), answerHash,
              answerDto.source() != null ? answerDto.source() : "human"));
        }
      }
    }
    if (!answersToSave.isEmpty()) {
      answerRepository.saveAll(answersToSave);
    }
    return answersToSave.size();
  }

  /**
   * Single vectorStore.add — one Ollama embedding request for the whole batch.
   */
  private void syncVectorStore(List<Question> saved) {
    List<Document> docs = saved.stream().map(QuestionDocuments::toDocument).toList();
    vectorStore.add(docs);
  }

  private ResolvedQuestion findOrCreate(QuestionDto dto,
      Map<String, Question> byExternalId,
      Map<String, Question> byContentHash) {
    String contentHash = ContentHash.sha256(dto.content());

    if (dto.externalId() != null && byExternalId.containsKey(dto.externalId())) {
      Question question = byExternalId.get(dto.externalId());
      boolean vectorStale = updateContentIfChanged(question, dto.content(), contentHash);
      return new ResolvedQuestion(question, false, vectorStale);
    }
    if (byContentHash.containsKey(contentHash)) {
      return new ResolvedQuestion(byContentHash.get(contentHash), false, false);
    }
    return new ResolvedQuestion(new Question(dto.content(), contentHash), true, false);
  }

  private void applyDtoFields(Question question, QuestionDto dto, Map<String, Skill> skillByName) {
    question.setExternalId(dto.externalId());
    question.setRequiresImpl(dto.requiresImpl());
    question.setLanguage(dto.language());
    question.setSkills(dto.skills().stream().map(skillByName::get)
        .filter(Objects::nonNull).collect(Collectors.toSet()));
  }

  /**
   * @return whether the content actually changed (and the vector-store entry is now stale).
   */
  private boolean updateContentIfChanged(Question question, String newContent,
      String newContentHash) {
    if (question.getContentHash().equals(newContentHash)) {
      return false;
    }
    question.setContent(newContent);
    question.setContentHash(newContentHash);
    return true;
  }

  private record ResolvedQuestion(Question question, boolean created, boolean vectorStale) {

  }

  public record Result(List<Question> questions, int created, int updated, int answersAdded) {

    static Result empty() {
      return new Result(List.of(), 0, 0, 0);
    }
  }
}
