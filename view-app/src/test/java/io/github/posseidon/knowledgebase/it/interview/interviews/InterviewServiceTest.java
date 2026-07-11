package io.github.posseidon.knowledgebase.it.interview.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;
import io.github.posseidon.knowledgebase.it.interview.domain.interview.Interview;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewView;
import io.github.posseidon.knowledgebase.it.interview.repo.InterviewRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewServiceTest {

  private InterviewRepository interviewRepository;
  private InterviewService service;

  @BeforeEach
  void setUp() {
    interviewRepository = mock(InterviewRepository.class);
    service = new InterviewService(interviewRepository, new QuestionMapper());
  }

  private static Interview interview(Decision decision) {
    Interview iv = new Interview();
    iv.setId(UUID.randomUUID());
    iv.setProjectCode("PROJ-1");
    iv.setDate(LocalDate.of(2026, 7, 9));
    iv.setFeedback("Some feedback text that is reasonably long for a snippet test case here.");
    iv.setUpskillingPlan("plan");
    iv.setDecision(decision);
    return iv;
  }

  @Test
  void findAllMapsAndOrdersByDate() {
    Interview iv = interview(Decision.GOOD_CANDIDATE);
    when(interviewRepository.findAllByOrderByDateAsc()).thenReturn(List.of(iv));

    List<InterviewView> views = service.findAll();

    assertThat(views).hasSize(1);
    assertThat(views.get(0).projectCode()).isEqualTo("PROJ-1");
    assertThat(views.get(0).dateDisplay()).contains("2026");
    assertThat(views.get(0).decisionCssClass()).isEqualTo("good");
    assertThat(views.get(0).decisionLabel()).isEqualTo("GOOD CANDIDATE");
  }

  @Test
  void groupsQuestionsBySkillWithGeneralFallback() {
    Interview iv = interview(Decision.MAYBE);
    Skill skill = new Skill("Java", "Java", null, null, null);
    skill.setId(UUID.randomUUID());
    Question withSkill = new Question("with skill", "hash1");
    withSkill.setId(UUID.randomUUID());
    withSkill.setSkills(Set.of(skill));
    Question withoutSkill = new Question("without skill", "hash2");
    withoutSkill.setId(UUID.randomUUID());
    iv.setQuestions(Set.of(withSkill, withoutSkill));
    when(interviewRepository.findAllByOrderByDateAsc()).thenReturn(List.of(iv));

    InterviewView view = service.findAll().get(0);

    assertThat(view.questionCount()).isEqualTo(2);
    assertThat(view.questionsBySkill()).extracting(g -> g.skillName())
        .containsExactlyInAnyOrder("Java", "General");
  }

  @Test
  void decisionCssClassAndLabelCoverAllValues() {
    for (Decision decision : Decision.values()) {
      Interview iv = interview(decision);
      when(interviewRepository.findAllByOrderByDateAsc()).thenReturn(List.of(iv));

      InterviewView view = service.findAll().get(0);

      assertThat(view.decisionCssClass()).isNotBlank();
      assertThat(view.decisionLabel()).isNotBlank();
    }
  }

  @Test
  void findByIdReturnsEmptyWhenMissing() {
    UUID id = UUID.randomUUID();
    when(interviewRepository.findById(id)).thenReturn(Optional.empty());

    assertThat(service.findById(id)).isEmpty();
  }

  @Test
  void findByIdMapsWhenPresent() {
    Interview iv = interview(Decision.NO_HIRE);
    when(interviewRepository.findById(iv.getId())).thenReturn(Optional.of(iv));

    Optional<InterviewView> view = service.findById(iv.getId());

    assertThat(view).isPresent();
    assertThat(view.get().id()).isEqualTo(iv.getId());
  }
}
