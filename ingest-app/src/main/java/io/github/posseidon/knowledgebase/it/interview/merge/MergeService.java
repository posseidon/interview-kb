package io.github.posseidon.knowledgebase.it.interview.merge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.posseidon.knowledgebase.it.interview.domain.merge.MergeLog;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.ingest.QuestionDocuments;
import io.github.posseidon.knowledgebase.it.interview.repo.MergeLogRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
                if (doc.getId().equals(q.getId().toString())) continue;
                try {
                    UUID candidateId = UUID.fromString(doc.getId());
                    raw.add(new MergeCandidate(q.getId(), candidateId, doc.getScore().floatValue()));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // One batched existence check instead of one existsById per candidate — the vector
        // store can retain stale entries for deleted questions, so this still needs checking.
        Set<UUID> candidateIds = raw.stream().map(MergeCandidate::targetId).collect(Collectors.toSet());
        Set<UUID> validIds = questionRepository.findExistingIds(candidateIds);
        return raw.stream().filter(c -> validIds.contains(c.targetId())).toList();
    }

    @Transactional
    public void merge(UUID targetId, UUID sourceId) {
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
        mergeLogRepository.save(new MergeLog(targetId, sourceSnapshot));

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

    public record MergeCandidate(UUID sourceId, UUID targetId, float similarity) {}
}
