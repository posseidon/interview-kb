package io.github.posseidon.knowledgebase.it.interview.ask;

import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.AskResponse;
import io.github.posseidon.knowledgebase.it.interview.dto.AnswerView;
import io.github.posseidon.knowledgebase.it.interview.dto.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AskService {

    private final VectorStore vectorStore;
    private final QuestionRepository questionRepository;

    public AskService(VectorStore vectorStore, QuestionRepository questionRepository) {
        this.vectorStore = vectorStore;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public AskResponse ask(String query) {
        // TODO: Extract {topics, tags, semanticQuery} via ChatClient JSON call

        // Semantic search
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(8)
                .similarityThreshold(0.4f)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        // Map Document IDs to question UUIDs and load entities
        Set<UUID> questionIds = results.stream()
                .map(doc -> {
                    try {
                        return UUID.fromString(doc.getId());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Question> questions = questionIds.stream()
                .map(questionRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted((q1, q2) -> {
                    Document d1 = results.stream()
                            .filter(d -> d.getId().equals(q1.getId().toString()))
                            .findFirst().orElse(null);
                    Document d2 = results.stream()
                            .filter(d -> d.getId().equals(q2.getId().toString()))
                            .findFirst().orElse(null);
                    if (d1 == null || d2 == null) return 0;
                    return Double.compare(d2.getScore(), d1.getScore());
                })
                .toList();

        // Convert to views
        List<QuestionView> sources = questions.stream()
                .map(this::toQuestionView)
                .toList();

        // TODO: Call ChatClient with questions + retrieved Q&A as grounding context

        String answer = "[Stub: LLM synthesis not yet implemented]";

        return new AskResponse(answer, sources);
    }

    private QuestionView toQuestionView(Question q) {
        List<String> topics = q.getTopics().stream()
                .map(t -> t.getSlug())
                .toList();
        List<String> tags = q.getTags().stream()
                .map(t -> t.getName())
                .toList();
        List<AnswerView> answers = q.getAnswers().stream()
                .map(a -> new AnswerView(a.getId(), a.getSource(), a.getContent()))
                .toList();

        return new QuestionView(
                q.getId(),
                q.getExternalId(),
                q.getContent(),
                q.getRequiresImpl(),
                q.getLanguage(),
                q.getFrequency(),
                topics,
                tags,
                answers
        );
    }
}
