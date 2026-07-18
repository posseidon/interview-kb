package io.github.posseidon.knowledgebase.it.interview.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class HandbookViewControllerTest {

  private SkillRepository skillRepository;
  private QuestionRepository questionRepository;
  private HandbookViewController controller;

  @BeforeEach
  void setUp() {
    skillRepository = mock(SkillRepository.class);
    questionRepository = mock(QuestionRepository.class);
    controller = new HandbookViewController(skillRepository, questionRepository,
        new QuestionMapper());
  }

  private static Question question(String content, Skill... skills) {
    Question q = new Question(content, "hash-" + content);
    q.setId(UUID.randomUUID());
    q.setSkills(Set.of(skills));
    return q;
  }

  @Test
  void homeDefaultsScopeToAllAndAddsTotalQuestions() {
    when(questionRepository.count()).thenReturn(42L);
    Model model = new ExtendedModelMap();

    String view = controller.home(null, model);

    assertThat(view).isEqualTo("question/home");
    assertThat(model.getAttribute("scope")).isEqualTo("all");
    assertThat(model.getAttribute("totalQuestions")).isEqualTo(42L);
  }

  @Test
  void homeKeepsExplicitScope() {
    when(questionRepository.count()).thenReturn(0L);
    Model model = new ExtendedModelMap();

    controller.home("coding", model);

    assertThat(model.getAttribute("scope")).isEqualTo("coding");
  }

  @Test
  void searchByFreeTextGroupsResultsBySkill() {
    Skill javaSkill = new Skill("Java", "Java", null, null, null);
    javaSkill.setId(UUID.randomUUID());
    Question q1 = question("q1", javaSkill);
    Question q2 = question("q2"); // no skill -> Uncategorized
    when(questionRepository.findFilteredBySkill(isNull(), eq("java"), any()))
        .thenReturn(new PageImpl<>(List.of(q1, q2)));

    Model model = new ExtendedModelMap();
    String view = controller.search("java", null, null, model);

    assertThat(view).isEqualTo("question/search");
    assertThat(model.getAttribute("query")).isEqualTo("java");
    assertThat(model.getAttribute("hasMatches")).isEqualTo(true);
    assertThat(model.getAttribute("totalCount")).isEqualTo(2);
    @SuppressWarnings("unchecked")
    List<HandbookViewController.SkillResultGroup> groups =
        (List<HandbookViewController.SkillResultGroup>) model.getAttribute("groups");
    assertThat(groups).extracting(HandbookViewController.SkillResultGroup::skillName)
        .containsExactlyInAnyOrder("Java", "Uncategorized");
  }

  @Test
  void searchBySkillIdUsesSkillNameAsDisplayQuery() {
    Skill skill = new Skill("Kafka", "Kafka", null, null, null);
    skill.setId(UUID.randomUUID());
    when(questionRepository.findBySkillId(eq(skill.getId()), any())).thenReturn(List.of());
    when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));

    Model model = new ExtendedModelMap();
    controller.search(null, skill.getId(), null, model);

    assertThat(model.getAttribute("query")).isEqualTo("Kafka");
    assertThat(model.getAttribute("hasMatches")).isEqualTo(false);
  }

  @Test
  void searchBySkillIdFallsBackToIdStringWhenSkillMissing() {
    UUID skillId = UUID.randomUUID();
    when(questionRepository.findBySkillId(eq(skillId), any())).thenReturn(List.of());
    when(skillRepository.findById(skillId)).thenReturn(Optional.empty());

    Model model = new ExtendedModelMap();
    controller.search(null, skillId, null, model);

    assertThat(model.getAttribute("query")).isEqualTo(skillId.toString());
  }

  @Test
  void searchWithNeitherQueryNorSkillReturnsEmptyResults() {
    Model model = new ExtendedModelMap();

    controller.search(null, null, null, model);

    assertThat(model.getAttribute("query")).isEqualTo("");
    assertThat(model.getAttribute("hasMatches")).isEqualTo(false);
  }

  @Test
  void searchAppliesScopeFilter() {
    Question implQuestion = question("impl");
    implQuestion.setRequiresImpl(true);
    when(questionRepository.findFilteredBySkill(isNull(), eq("q"), any()))
        .thenReturn(new PageImpl<>(List.of(implQuestion)));

    Model model = new ExtendedModelMap();
    controller.search("q", null, "theory", model);

    assertThat(model.getAttribute("totalCount")).isEqualTo(0);
  }

  @Test
  void detailThrowsNotFoundWhenMissing() {
    UUID id = UUID.randomUUID();
    when(questionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.detail(id, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void detailAddsQuestionAndSortedAnswerDetails() {
    Question q = question("what is java?");
    Answer older = new Answer(q, "older answer", "hash-o", "human");
    older.setId(UUID.randomUUID());
    older.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    Answer newer = new Answer(q, "newer answer", "hash-n", "human");
    newer.setId(UUID.randomUUID());
    newer.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));
    q.setAnswers(Set.of(newer, older));
    when(questionRepository.findById(q.getId())).thenReturn(Optional.of(q));

    Model model = new ExtendedModelMap();
    String view = controller.detail(q.getId(), model);

    assertThat(view).isEqualTo("question/question-detail");
    @SuppressWarnings("unchecked")
    List<HandbookViewController.AnswerDetail> answerDetails =
        (List<HandbookViewController.AnswerDetail>) model.getAttribute("answerDetails");
    assertThat(answerDetails).extracting(HandbookViewController.AnswerDetail::rawContent)
        .containsExactly("older answer", "newer answer");
    assertThat(model.getAttribute("createdAtDisplay")).isNotNull();
  }
}
