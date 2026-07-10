package io.github.posseidon.knowledgebase.it.interview.util;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.question.AnswerView;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.dto.question.SkillRef;
import org.springframework.stereotype.Component;

@Component
public class QuestionMapper {

    public QuestionView toView(Question q) {
        return new QuestionView(
                q.getId(), q.getExternalId(), q.getContent(),
                q.isRequiresImpl(), q.getLanguage(), q.getFrequency(),
                q.getSkills().stream().map(s -> new SkillRef(s.getId(), s.getName())).toList(),
                q.getAnswers().stream()
                        .map(a -> new AnswerView(a.getId(), a.getSource(), a.getContent()))
                        .toList()
        );
    }
}
