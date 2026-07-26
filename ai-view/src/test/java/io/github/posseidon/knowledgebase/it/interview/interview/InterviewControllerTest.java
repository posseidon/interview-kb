package io.github.posseidon.knowledgebase.it.interview.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.search.SemanticSearchService;
import io.github.posseidon.knowledgebase.it.interview.web.GenerationStatusView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class InterviewControllerTest {

  private SemanticSearchService semanticSearchService;
  private QuestionInterviewService interviewService;
  private InterviewGenerationStore interviewStore;
  private InterviewController controller;
  private MockHttpSession session;

  private static Question questionWith(String content) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    return question;
  }

  @BeforeEach
  void setUp() {
    semanticSearchService = mock(SemanticSearchService.class);
    interviewService = mock(QuestionInterviewService.class);
    interviewStore = new InterviewGenerationStore();
    controller = new InterviewController(semanticSearchService, interviewService, interviewStore);
    session = new MockHttpSession();
  }

  @Test
  void generateInterviewQuestionsDispatchesWhenMatchesExist() {
    Question question = questionWith("What is a partition?");
    when(semanticSearchService.search(eq("kafka"), anyInt()))
        .thenReturn(List.of(question));

    String result = controller.generateInterviewQuestions("kafka", "Senior", 5, session);

    assertThat(result).isEqualTo("redirect:/questions?q=kafka");
    verify(interviewService).generateAsync(session.getId() + ":kafka", List.of(question),
        "Senior", 5);
  }

  @Test
  void generateInterviewQuestionsClampsCountToAllowedRange() {
    Question question = questionWith("Question");
    when(semanticSearchService.search(eq("kafka"), anyInt()))
        .thenReturn(List.of(question));

    controller.generateInterviewQuestions("kafka", "Senior", 50, session);

    verify(interviewService).generateAsync(eq(session.getId() + ":kafka"), any(), eq("Senior"),
        eq(10));
  }

  @Test
  void generateInterviewQuestionsSkipsDispatchWhenNoMatches() {
    when(semanticSearchService.search(eq("kafka"), anyInt())).thenReturn(List.of());

    controller.generateInterviewQuestions("kafka", "Senior", 5, session);

    verify(interviewService, never()).generateAsync(any(), any(), any(), anyInt());
  }

  @Test
  void interviewQuestionsStatusReportsPendingState() {
    String storeKey = session.getId() + ":kafka";
    interviewStore.begin(storeKey);
    interviewStore.step(storeKey, "Generating 5 interview questions…");

    GenerationStatusView status = controller.interviewQuestionsStatus("kafka", session);

    assertThat(status.pending()).isTrue();
    assertThat(status.step()).isEqualTo("Generating 5 interview questions…");
  }

  @Test
  void interviewQuestionsStatusReportsNotPendingByDefault() {
    GenerationStatusView status = controller.interviewQuestionsStatus("kafka", session);

    assertThat(status.pending()).isFalse();
    assertThat(status.step()).isNull();
  }
}
