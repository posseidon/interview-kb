package io.github.posseidon.knowledgebase.it.interview.merge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.posseidon.knowledgebase.it.interview.domain.merge.MergeLog;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.vectorstore.QuestionDocuments;
import io.github.posseidon.knowledgebase.it.interview.repo.MergeLogRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.VectorStoreIds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MergeService {

  private final QuestionRepository questionRepository;
  private final MergeLogRepository mergeLogRepository;
  private final VectorStore vectorStore;
  private final ObjectMapper objectMapper;

  public MergeService(QuestionRepository questionRepository, MergeLogRepository mergeLogRepository,
      VectorStore vectorStore, ObjectMapper objectMapper) {
    this.questionRepository = questionRepository;
    this.mergeLogRepository = mergeLogRepository;
    this.vectorStore = vectorStore;
    this.objectMapper = objectMapper;
  }

  public List<MergeCandidate> findCandidates(float threshold) {
    List<Question> allQuestions = questionRepository.findAll();
    List<MergeCandidate> raw = new ArrayList<>();
    for (Question q : allQuestions) {
      SearchRequest request = SearchRequest.builder()
          .query(q.getContent()).topK(5).similarityThreshold(threshold).build();
      List<Document> results = vectorStore.similaritySearch(request);
      for (Document doc : results) {
        if (doc.getId().equals(q.getId().toString())) {
          continue;
        }
        VectorStoreIds.parse(doc.getId()).ifPresent(candidateId ->
            raw.add(new MergeCandidate(q.getId(), candidateId, doc.getScore().floatValue())));
      }
    }

    // One batched existence check instead of one existsById per candidate — the vector
    // store can retain stale entries for deleted questions, so this still needs checking.
    Set<UUID> candidateIds = raw.stream().map(MergeCandidate::targetId).collect(Collectors.toSet());
    Set<UUID> validIds = questionRepository.findExistingIds(candidateIds);
    return raw.stream().filter(c -> validIds.contains(c.targetId())).toList();
  }

  public void merge(UUID targetId, UUID sourceId) {
    merge(targetId, sourceId, null, null);
  }

  /**
   * Like {@link #merge(UUID, UUID)}, but records the similarity score and a free-text note
   * (e.g. "auto-merged during ingestion") on the {@link MergeLog} entry — for merges triggered
   * automatically, without human review, where an audit trail of *why* matters more than for the
   * reviewed {@code /merge} flow.
   */
  @Transactional
  public void merge(UUID targetId, UUID sourceId, Float similarity, String note) {
    Question target = questionRepository.findById(targetId)
        .orElseThrow(() -> new IllegalArgumentException("Target question not found"));
    Question source = questionRepository.findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("Source question not found"));

    String sourceSnapshot;
    try {
      sourceSnapshot = objectMapper.writeValueAsString(source);
    } catch (Exception e) {
      sourceSnapshot = source.toString();
    }
    MergeLog mergeLog = new MergeLog(targetId, sourceSnapshot);
    mergeLog.setSimilarity(similarity);
    mergeLog.setNote(note);
    mergeLogRepository.save(mergeLog);

    for (Answer answer : new HashSet<>(source.getAnswers())) {
      answer.setQuestion(target);
      target.getAnswers().add(answer);
    }
    target.getSkills().addAll(source.getSkills());
    target.setFrequency(target.getFrequency() + source.getFrequency());
    questionRepository.save(target);

    vectorStore.delete(List.of(sourceId.toString()));
    vectorStore.add(List.of(QuestionDocuments.toDocument(target)));

    questionRepository.deleteById(sourceId);
  }

  public record MergeCandidate(UUID sourceId, UUID targetId, float similarity) {

  }
}
