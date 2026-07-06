package io.github.posseidon.knowledgebase.it.interview.merge;

import io.github.posseidon.knowledgebase.it.interview.domain.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.MergeLog;
import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.MergeLogRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                        VectorStore vectorStore) {
        this.questionRepository = questionRepository;
        this.mergeLogRepository = mergeLogRepository;
        this.vectorStore = vectorStore;
        this.objectMapper = new ObjectMapper();
    }

    public List<MergeCandidate> findCandidates(float threshold) {
        List<Question> allQuestions = questionRepository.findAll();
        List<MergeCandidate> candidates = new ArrayList<>();

        for (Question q : allQuestions) {
            SearchRequest request = SearchRequest.builder()
                    .query(q.getContent())
                    .topK(5)
                    .similarityThreshold(threshold)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);

            for (Document doc : results) {
                if (doc.getId().equals(q.getId().toString())) continue;

                try {
                    UUID candidateId = UUID.fromString(doc.getId());
                    Optional<Question> candidate = questionRepository.findById(candidateId);
                    if (candidate.isPresent()) {
                        candidates.add(new MergeCandidate(
                                q.getId(),
                                candidateId,
                                doc.getScore().floatValue()
                        ));
                    }
                } catch (IllegalArgumentException e) {
                    // Skip invalid UUIDs
                }
            }
        }

        return candidates;
    }

    @Transactional
    public void merge(UUID targetId, UUID sourceId) {
        Question target = questionRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target question not found"));
        Question source = questionRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source question not found"));

        // Snapshot source into merge_log
        String sourceSnapshot;
        try {
            sourceSnapshot = objectMapper.writeValueAsString(source);
        } catch (Exception e) {
            sourceSnapshot = source.toString();
        }

        MergeLog log = new MergeLog(targetId, sourceSnapshot);
        mergeLogRepository.save(log);

        // Move answers from source to target
        for (Answer answer : new HashSet<>(source.getAnswers())) {
            answer.setQuestion(target);
            target.getAnswers().add(answer);
        }

        // Union topics and tags
        target.getTopics().addAll(source.getTopics());
        target.getTags().addAll(source.getTags());

        // Increment frequency
        target.setFrequency(target.getFrequency() + source.getFrequency());

        questionRepository.save(target);

        // Update vector store: delete source, re-add target
        vectorStore.delete(List.of(sourceId.toString()));

        Map<String, Object> metadata = Map.of(
                "topics", target.getTopics().stream().map(t -> t.getSlug()).toList(),
                "tags", target.getTags().stream().map(t -> t.getName()).toList(),
                "frequency", target.getFrequency()
        );
        Document doc = Document.builder()
                .id(target.getId().toString())
                .text(target.getContent())
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(doc));

        // Hard delete source
        questionRepository.deleteById(sourceId);
    }

    public record MergeCandidate(UUID sourceId, UUID targetId, float similarity) {}
}
