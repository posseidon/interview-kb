package io.github.posseidon.knowledgebase.it.interview.ask;

import io.github.posseidon.knowledgebase.it.interview.dto.ask.AskResponse;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AskService {

    private final VectorStore vectorStore;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public AskService(VectorStore vectorStore, QuestionRepository questionRepository,
                      QuestionMapper questionMapper) {
        this.vectorStore = vectorStore;
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @Transactional(readOnly = true)
    public AskResponse ask(String query) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(8).similarityThreshold(0.4f).build());

        Map<String, Double> scoreById = new HashMap<>();
        List<UUID> questionIds = new ArrayList<>();
        for (Document doc : results) {
            try {
                UUID id = UUID.fromString(doc.getId());
                questionIds.add(id);
                scoreById.put(doc.getId(), doc.getScore());
            } catch (IllegalArgumentException ignored) {}
        }

        List<QuestionView> sources = questionRepository.findAllById(questionIds).stream()
                .sorted(Comparator.comparingDouble(
                        q -> -scoreById.getOrDefault(q.getId().toString(), 0.0)))
                .map(questionMapper::toView)
                .toList();

        // TODO: Call ChatClient with retrieved Q&A as grounding context
        return new AskResponse("[Stub: LLM synthesis not yet implemented]", sources);
    }
}
