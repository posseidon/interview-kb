package io.github.posseidon.knowledgebase.it.interview.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.interview.InterviewGenerationStore;
import io.github.posseidon.knowledgebase.it.interview.interview.InterviewQuestion;
import io.github.posseidon.knowledgebase.it.interview.quiz.Quiz;
import io.github.posseidon.knowledgebase.it.interview.quiz.QuizGenerationStore;
import io.github.posseidon.knowledgebase.it.interview.quiz.QuizQuestion;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.search.SemanticSearchService;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class QuestionControllerTest {

  private SemanticSearchService semanticSearchService;
  private QuestionRepository questionRepository;
  private QuizGenerationStore quizStore;
  private InterviewGenerationStore interviewStore;
  private QuestionController controller;
  private MockHttpSession session;

  private static Question questionWith(String content) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    return question;
  }

  @BeforeEach
  void setUp() {
    semanticSearchService = mock(SemanticSearchService.class);
    questionRepository = mock(QuestionRepository.class);
    quizStore = new QuizGenerationStore();
    interviewStore = new InterviewGenerationStore();
    controller = new QuestionController(semanticSearchService, questionRepository,
        new QuestionMapper(), quizStore, interviewStore);
    session = new MockHttpSession();
  }

  @Test
  void searchReturnsEmptyStateForBlankQuery() {
    Model model = new ExtendedModelMap();

    String view = controller.search(null, session, model);

    assertThat(view).isEqualTo("question/search");
    assertThat(model.getAttribute("hasMatches")).isEqualTo(false);
    assertThat(model.getAttribute("query")).isEqualTo("");
  }

  @Test
  void searchReturnsNumberedResultsForQuery() {
    Question q1 = questionWith("First");
    Question q2 = questionWith("Second");
    when(semanticSearchService.searchExpandingQuery(eq("kafka ordering"), anyInt()))
        .thenReturn(List.of(q1, q2));

    Model model = new ExtendedModelMap();
    controller.search("kafka ordering", session, model);

    @SuppressWarnings("unchecked")
    List<QuestionController.QuestionResultItem> results =
        (List<QuestionController.QuestionResultItem>) model.getAttribute("results");
    assertThat(results).hasSize(2);
    assertThat(results.get(0).number()).isEqualTo(1);
    assertThat(results.get(1).number()).isEqualTo(2);
  }

  @Test
  void searchExposesSkillLevelsForTheSeniorityPicker() {
    Model model = new ExtendedModelMap();

    controller.search(null, session, model);

    assertThat((SkillLevel[]) model.getAttribute("levels")).containsExactly(SkillLevel.NOVICE,
        SkillLevel.INTERMEDIATE, SkillLevel.ADVANCED, SkillLevel.EXPERT);
  }

  @Test
  void searchIncludesStoredInterviewSetResultWhenPresent() {
    Question question = questionWith("First");
    when(semanticSearchService.searchExpandingQuery(eq("kafka ordering"), anyInt()))
        .thenReturn(List.of(question));
    String storeKey = session.getId() + ":kafka ordering";
    List<InterviewQuestion> interviewSet = List.of(
        new InterviewQuestion("What is X?", "answer", List.of(question.getId().toString()),
            List.of()));
    interviewStore.begin(storeKey);
    interviewStore.complete(storeKey, interviewSet);

    Model model = new ExtendedModelMap();
    controller.search("kafka ordering", session, model);

    assertThat(model.getAttribute("interviewSet")).isEqualTo(interviewSet);
  }

  @Test
  void searchLeavesInterviewSetNullWhenNoneStored() {
    when(semanticSearchService.searchExpandingQuery(eq("kafka ordering"), anyInt()))
        .thenReturn(List.of(questionWith("First")));

    Model model = new ExtendedModelMap();
    controller.search("kafka ordering", session, model);

    assertThat(model.getAttribute("interviewSet")).isNull();
  }

  @Test
  void detailThrowsNotFoundWhenQuestionMissing() {
    UUID id = UUID.randomUUID();
    when(questionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.detail(id, session, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void detailPopulatesModelWithQuestionAndAnswers() {
    Question question = questionWith("What is a partition?");
    question.getAnswers()
        .add(new Answer(question, "It's a unit of parallelism.", "hash", "human"));
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    Model model = new ExtendedModelMap();
    String view = controller.detail(question.getId(), session, model);

    assertThat(view).isEqualTo("question/question-detail");
    assertThat((String) model.getAttribute("questionContentHtml"))
        .contains("What is a partition?");
    @SuppressWarnings("unchecked")
    List<QuestionController.AnswerDetail> answers =
        (List<QuestionController.AnswerDetail>) model.getAttribute("answerDetails");
    assertThat(answers).hasSize(1);
  }

  @Test
  void detailIncludesStoredQuizResultWhenPresent() {
    Question question = questionWith("Question with quiz");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));
    String storeKey = session.getId() + ":" + question.getId();
    Quiz quiz = new Quiz(List.of(
        new QuizQuestion("What is X?", List.of("a", "b", "c", "d"), 0, "because")));
    quizStore.begin(storeKey);
    quizStore.complete(storeKey, quiz);

    Model model = new ExtendedModelMap();
    controller.detail(question.getId(), session, model);

    assertThat(model.getAttribute("quiz")).isEqualTo(quiz);
  }

  @Test
  void detailLeavesQuizResultNullWhenNoneStored() {
    Question question = questionWith("No quiz yet");
    when(questionRepository.findById(question.getId())).thenReturn(Optional.of(question));

    Model model = new ExtendedModelMap();
    controller.detail(question.getId(), session, model);

    assertThat(model.getAttribute("quiz")).isNull();
  }
}
