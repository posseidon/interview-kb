package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.interview.InterviewGenerationStore;
import io.github.posseidon.knowledgebase.it.interview.interview.InterviewQuestion;
import io.github.posseidon.knowledgebase.it.interview.interview.QuestionInterviewService;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.web.GenerationStatusView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class SkillControllerTest {

  private SkillRepository skillRepository;
  private QuestionRepository questionRepository;
  private QuestionInterviewService interviewService;
  private InterviewGenerationStore interviewStore;
  private SkillController controller;
  private MockHttpSession session;

  private static Skill skillWith(String name, String description) {
    Skill skill = new Skill(name, "path-" + name.hashCode(), description, null, null);
    skill.setId(UUID.randomUUID());
    return skill;
  }

  private static Skill skillWithCriteria(String name, String noviceCriteria,
      String intermediateCriteria, String advancedCriteria, String expertCriteria) {
    Skill skill = skillWith(name, null);
    skill.setNoviceCriteria(noviceCriteria);
    skill.setIntermediateCriteria(intermediateCriteria);
    skill.setAdvancedCriteria(advancedCriteria);
    skill.setExpertCriteria(expertCriteria);
    return skill;
  }

  private static Question questionWith(String content) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    return question;
  }

  @BeforeEach
  void setUp() {
    skillRepository = mock(SkillRepository.class);
    questionRepository = mock(QuestionRepository.class);
    interviewService = mock(QuestionInterviewService.class);
    interviewStore = new InterviewGenerationStore();
    controller = new SkillController(skillRepository, questionRepository, interviewService,
        interviewStore);
    session = new MockHttpSession();
  }

  @Test
  void searchReturnsEmptyStateForBlankQuery() {
    Model model = new ExtendedModelMap();

    String view = controller.search(null, model);

    assertThat(view).isEqualTo("skill/search");
    assertThat(model.getAttribute("hasMatches")).isEqualTo(false);
    assertThat(model.getAttribute("query")).isEqualTo("");
  }

  @Test
  void searchReturnsMatchingSkills() {
    Skill kafka = skillWith("Kafka", "Distributed streaming platform");
    when(skillRepository.search(eq("kafka"), anyInt())).thenReturn(List.of(kafka));

    Model model = new ExtendedModelMap();
    controller.search("kafka", model);

    @SuppressWarnings("unchecked")
    List<SkillController.SkillResultItem> results =
        (List<SkillController.SkillResultItem>) model.getAttribute("results");
    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("Kafka");
    assertThat(results.get(0).description()).isEqualTo("Distributed streaming platform");
  }

  @Test
  void detailThrowsNotFoundWhenSkillMissing() {
    UUID id = UUID.randomUUID();
    when(skillRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.detail(id, session, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void detailPopulatesModelWithSkillAndQuestionCount() {
    Skill skill = skillWith("Kafka", "Distributed streaming platform");
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of(questionWith("Q1"), questionWith("Q2")));

    Model model = new ExtendedModelMap();
    String view = controller.detail(skill.getId(), session, model);

    assertThat(view).isEqualTo("skill/skill-detail");
    assertThat(model.getAttribute("skill")).isEqualTo(skill);
    assertThat(model.getAttribute("hasQuestions")).isEqualTo(true);
    assertThat(model.getAttribute("questionCount")).isEqualTo(2);
  }

  @Test
  void detailExposesCriteriaForAllFourLevelsAtOnce() {
    Skill skill = skillWithCriteria("Kafka", "Knows what a broker is.", "Can tune consumers.",
        "Designs partition strategy.", "Owns cluster capacity planning.");
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.detail(skill.getId(), session, model);

    @SuppressWarnings("unchecked")
    Map<SkillLevel, String> criteriaByLevel =
        (Map<SkillLevel, String>) model.getAttribute("criteriaByLevel");
    assertThat(criteriaByLevel.get(SkillLevel.NOVICE)).isEqualTo("Knows what a broker is.");
    assertThat(criteriaByLevel.get(SkillLevel.INTERMEDIATE)).isEqualTo("Can tune consumers.");
    assertThat(criteriaByLevel.get(SkillLevel.ADVANCED)).isEqualTo("Designs partition strategy.");
    assertThat(criteriaByLevel.get(SkillLevel.EXPERT))
        .isEqualTo("Owns cluster capacity planning.");
  }

  @Test
  void detailReportsEmptyCriteriaForLevelsWithNoneDefined() {
    Skill skill = skillWithCriteria("Kafka", "Knows what a broker is.", null, null, null);
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.detail(skill.getId(), session, model);

    @SuppressWarnings("unchecked")
    Map<SkillLevel, String> criteriaByLevel =
        (Map<SkillLevel, String>) model.getAttribute("criteriaByLevel");
    assertThat(criteriaByLevel.get(SkillLevel.EXPERT)).isEmpty();
  }

  @Test
  void detailExposesAllFourLevelsForThePicker() {
    Skill skill = skillWith("Kafka", null);
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.detail(skill.getId(), session, model);

    assertThat((SkillLevel[]) model.getAttribute("levels")).containsExactly(SkillLevel.NOVICE,
        SkillLevel.INTERMEDIATE, SkillLevel.ADVANCED, SkillLevel.EXPERT);
  }

  @Test
  void detailReportsNoQuestionsWhenSkillHasNoneTagged() {
    Skill skill = skillWith("Rare Skill", null);
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.detail(skill.getId(), session, model);

    assertThat(model.getAttribute("hasQuestions")).isEqualTo(false);
    assertThat(model.getAttribute("questionCount")).isEqualTo(0);
  }

  @Test
  void detailIncludesStoredInterviewSetResultWhenPresent() {
    Skill skill = skillWith("Kafka", null);
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of(questionWith("Q1")));
    String storeKey = session.getId() + ":" + skill.getId();
    List<InterviewQuestion> interviewSet = List.of(
        new InterviewQuestion("What is a partition?", "answer", List.of(UUID.randomUUID()
            .toString()), List.of()));
    interviewStore.begin(storeKey);
    interviewStore.complete(storeKey, interviewSet);

    Model model = new ExtendedModelMap();
    controller.detail(skill.getId(), session, model);

    assertThat(model.getAttribute("interviewSet")).isEqualTo(interviewSet);
  }

  @Test
  void generateInterviewQuestionsDispatchesWhenSkillHasQuestions() {
    Skill skill = skillWith("Kafka", null);
    Question question = questionWith("What is a partition?");
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of(question));

    String result = controller.generateInterviewQuestions(skill.getId(), "Senior", 5, session);

    assertThat(result).isEqualTo("redirect:/skills/" + skill.getId());
    verify(interviewService).generateAsync(session.getId() + ":" + skill.getId(),
        List.of(question), "Senior", 5);
  }

  @Test
  void generateInterviewQuestionsClampsCountToAllowedRange() {
    Skill skill = skillWith("Kafka", null);
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of(questionWith("Q1")));

    controller.generateInterviewQuestions(skill.getId(), "Senior", 50, session);

    verify(interviewService).generateAsync(eq(session.getId() + ":" + skill.getId()), any(),
        eq("Senior"), eq(10));
  }

  @Test
  void generateInterviewQuestionsSkipsDispatchWhenSkillHasNoQuestions() {
    Skill skill = skillWith("Rare Skill", null);
    when(questionRepository.findBySkillId(eq(skill.getId()), any(Pageable.class)))
        .thenReturn(List.of());

    controller.generateInterviewQuestions(skill.getId(), "Senior", 5, session);

    verify(interviewService, never()).generateAsync(any(), any(), any(), anyInt());
  }

  @Test
  void interviewQuestionsStatusReportsPendingState() {
    UUID skillId = UUID.randomUUID();
    String storeKey = session.getId() + ":" + skillId;
    interviewStore.begin(storeKey);
    interviewStore.step(storeKey, "Generating 5 interview questions…");

    GenerationStatusView status = controller.interviewQuestionsStatus(skillId, session);

    assertThat(status.pending()).isTrue();
    assertThat(status.step()).isEqualTo("Generating 5 interview questions…");
  }

  @Test
  void interviewQuestionsStatusReportsNotPendingByDefault() {
    GenerationStatusView status = controller.interviewQuestionsStatus(UUID.randomUUID(), session);

    assertThat(status.pending()).isFalse();
    assertThat(status.step()).isNull();
  }
}
