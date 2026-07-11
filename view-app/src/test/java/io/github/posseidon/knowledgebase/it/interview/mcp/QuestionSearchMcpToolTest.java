package io.github.posseidon.knowledgebase.it.interview.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class QuestionSearchMcpToolTest {

  @Test
  void searchesAndAppliesCodingScopeFilter() {
    QuestionRepository questionRepository = mock(QuestionRepository.class);
    QuestionSearchMcpTool tool = new QuestionSearchMcpTool(questionRepository,
        new QuestionMapper());

    Question codingQuestion = new Question("implement a stack", "hash1");
    codingQuestion.setId(UUID.randomUUID());
    codingQuestion.setRequiresImpl(true);
    Question theoryQuestion = new Question("what is a stack", "hash2");
    theoryQuestion.setId(UUID.randomUUID());
    theoryQuestion.setRequiresImpl(false);

    when(questionRepository.findFilteredBySkill(isNull(), eq("stack"), any()))
        .thenReturn(new PageImpl<>(List.of(codingQuestion, theoryQuestion)));

    List<QuestionView> results = tool.searchQuestions("stack", "coding");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).requiresImpl()).isTrue();
  }

  @Test
  void returnsAllResultsWhenScopeOmitted() {
    QuestionRepository questionRepository = mock(QuestionRepository.class);
    QuestionSearchMcpTool tool = new QuestionSearchMcpTool(questionRepository,
        new QuestionMapper());

    Question question = new Question("content", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findFilteredBySkill(isNull(), eq("query"), any()))
        .thenReturn(new PageImpl<>(List.of(question)));

    List<QuestionView> results = tool.searchQuestions("query", null);

    assertThat(results).hasSize(1);
  }
}
