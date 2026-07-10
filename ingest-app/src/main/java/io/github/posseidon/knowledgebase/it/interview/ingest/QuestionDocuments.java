package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import org.springframework.ai.document.Document;

import java.util.Map;

/** The single Spring AI {@link Document} shape mirrored into {@code vector_store} for a question. */
public final class QuestionDocuments {

    private QuestionDocuments() {}

    public static Document toDocument(Question q) {
        return Document.builder()
                .id(q.getId().toString())
                .text(q.getContent())
                .metadata(Map.of(
                        "skills", q.getSkills().stream().map(Skill::getName).toList(),
                        "frequency", q.getFrequency()))
                .build();
    }
}
