package io.github.posseidon.knowledgebase.it.interview.mcp;

import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class QuestionSearchMcpTool {

    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public QuestionSearchMcpTool(QuestionRepository questionRepository, QuestionMapper questionMapper) {
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @Tool(name = "search_questions",
            description = "Free-text search across all interview questions in the knowledge base's content, "
                    + "optionally scoped to coding or theory questions.")
    public List<QuestionView> searchQuestions(
            @ToolParam(description = "Free-text keyword to search for in question content") String query,
            @ToolParam(required = false,
                    description = "Optional scope filter: 'coding' for implementation questions, "
                            + "'theory' for non-implementation questions. Omit for both.") String scope) {
        PageRequest page = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "frequency"));
        List<QuestionView> results = questionRepository.findFilteredBySkill(null, query, page)
                .stream().map(questionMapper::toView).toList();

        if ("coding".equals(scope)) {
            results = results.stream().filter(QuestionView::requiresImpl).toList();
        } else if ("theory".equals(scope)) {
            results = results.stream().filter(r -> !r.requiresImpl()).toList();
        }

        return results;
    }
}
