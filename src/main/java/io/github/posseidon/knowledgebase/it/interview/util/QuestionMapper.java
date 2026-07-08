package io.github.posseidon.knowledgebase.it.interview.util;

import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.Tag;
import io.github.posseidon.knowledgebase.it.interview.domain.Topic;
import io.github.posseidon.knowledgebase.it.interview.dto.AnswerView;
import io.github.posseidon.knowledgebase.it.interview.dto.QuestionView;
import org.springframework.stereotype.Component;

@Component
public class QuestionMapper {

    public QuestionView toView(Question q) {
        return new QuestionView(
                q.getId(), q.getExternalId(), q.getContent(),
                q.getRequiresImpl(), q.getLanguage(), q.getFrequency(),
                q.getTopics().stream().map(Topic::getSlug).toList(),
                q.getTags().stream().map(Tag::getName).toList(),
                q.getAnswers().stream()
                        .map(a -> new AnswerView(a.getId(), a.getSource(), a.getContent()))
                        .toList()
        );
    }
}
