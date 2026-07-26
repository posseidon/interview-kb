package io.github.posseidon.knowledgebase.it.interview.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class QuizControllerTest {

  private QuestionRepository questionRepository;
  private QuestionQuizService quizService;
  private QuizGenerationStore quizStore;
  private QuizController controller;
  private MockHttpSession session;

  private static Question questionWith(String content) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    return question;
  }

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    quizService = mock(QuestionQuizService.class);
    quizStore = new QuizGenerationStore();
    controller = new QuizController(questionRepository, quizService, quizStore);
    session = new MockHttpSession();
  }

  @Test
  void generateQuizDispatchesWhenAnswersExist() {
    Question question = questionWith("Question");
    question.getAnswers().add(new Answer(question, "answer", "hash", "human"));
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    String result = controller.generateQuiz(question.getId(), session);

    assertThat(result).isEqualTo("redirect:/questions/" + question.getId());
    verify(quizService).generateAsync(session.getId() + ":" + question.getId(), "Question",
        List.of("answer"));
  }

  @Test
  void generateQuizSkipsDispatchWhenNoAnswers() {
    Question question = questionWith("Question");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    controller.generateQuiz(question.getId(), session);

    verify(quizService, never()).generateAsync(any(), any(), any());
  }

  @Test
  void quizStatusReportsPendingState() {
    UUID id = UUID.randomUUID();
    String storeKey = session.getId() + ":" + id;
    quizStore.begin(storeKey);
    quizStore.step(storeKey, "Generating 5 quiz questions…");
    quizStore.targetCount(storeKey, 5);

    QuizStatusView status = controller.quizStatus(id, session);

    assertThat(status.pending()).isTrue();
    assertThat(status.step()).isEqualTo("Generating 5 quiz questions…");
    assertThat(status.targetCount()).isEqualTo(5);
    assertThat(status.questions()).isEmpty();
  }

  @Test
  void quizStatusReportsQuestionsGeneratedSoFar() {
    UUID id = UUID.randomUUID();
    String storeKey = session.getId() + ":" + id;
    quizStore.begin(storeKey);
    quizStore.targetCount(storeKey, 3);
    QuizQuestion firstQuestion = new QuizQuestion("q1", List.of("a", "b", "c", "d"), 0, "because");
    quizStore.appendQuestions(storeKey, List.of(firstQuestion));

    QuizStatusView status = controller.quizStatus(id, session);

    assertThat(status.pending()).isTrue();
    assertThat(status.questions()).containsExactly(firstQuestion);
  }

  @Test
  void quizStatusReportsNotPendingByDefault() {
    QuizStatusView status = controller.quizStatus(UUID.randomUUID(), session);

    assertThat(status.pending()).isFalse();
    assertThat(status.step()).isNull();
    assertThat(status.questions()).isEmpty();
    assertThat(status.targetCount()).isZero();
  }
}
