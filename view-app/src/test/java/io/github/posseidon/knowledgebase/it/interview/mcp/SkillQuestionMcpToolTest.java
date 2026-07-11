package io.github.posseidon.knowledgebase.it.interview.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillQuestionMcpToolTest {

  private SkillRepository skillRepository;
  private QuestionRepository questionRepository;
  private SkillQuestionMcpTool tool;

  @BeforeEach
  void setUp() {
    skillRepository = mock(SkillRepository.class);
    questionRepository = mock(QuestionRepository.class);
    tool = new SkillQuestionMcpTool(skillRepository, questionRepository, new QuestionMapper());
  }

  @Test
  void returnsNotFoundForBlankSkillName() {
    var result = tool.findQuestionsForSkill("  ", "ADVANCED");

    assertThat(result.found()).isFalse();
  }

  @Test
  void returnsNotFoundWhenNoSkillMatches() {
    when(skillRepository.search(anyString(), anyInt())).thenReturn(List.of());

    var result = tool.findQuestionsForSkill("NonexistentSkill", "ADVANCED");

    assertThat(result.found()).isFalse();
    assertThat(result.skillName()).isEqualTo("NonexistentSkill");
  }

  @Test
  void defaultsToIntermediateWhenLevelOmitted() {
    Skill skill = new Skill("Java", "Java", null, null, null);
    skill.setId(UUID.randomUUID());
    when(skillRepository.search(anyString(), anyInt())).thenReturn(List.of(skill));
    when(questionRepository.findBySkillIdAndLevel(eq(skill.getId()), eq("INTERMEDIATE")))
        .thenReturn(List.of());

    var result = tool.findQuestionsForSkill("Java", null);

    assertThat(result.found()).isTrue();
    assertThat(result.level()).isEqualTo("INTERMEDIATE");
  }

  @Test
  void defaultsToIntermediateWhenLevelUnparseable() {
    Skill skill = new Skill("Java", "Java", null, null, null);
    skill.setId(UUID.randomUUID());
    when(skillRepository.search(anyString(), anyInt())).thenReturn(List.of(skill));
    when(questionRepository.findBySkillIdAndLevel(eq(skill.getId()), eq("INTERMEDIATE")))
        .thenReturn(List.of());

    var result = tool.findQuestionsForSkill("Java", "not-a-level");

    assertThat(result.level()).isEqualTo("INTERMEDIATE");
  }

  @Test
  void resolvesRequestedLevelAndReturnsQuestions() {
    Skill skill = new Skill("Kafka", "Kafka", null, null, null);
    skill.setId(UUID.randomUUID());
    when(skillRepository.search(anyString(), anyInt())).thenReturn(List.of(skill));
    Question question = new Question("advanced kafka question", "hash");
    question.setId(UUID.randomUUID());
    when(questionRepository.findBySkillIdAndLevel(eq(skill.getId()), eq("EXPERT")))
        .thenReturn(List.of(question));

    var result = tool.findQuestionsForSkill("Kafka", "expert");

    assertThat(result.found()).isTrue();
    assertThat(result.skillId()).isEqualTo(skill.getId());
    assertThat(result.level()).isEqualTo("EXPERT");
    assertThat(result.questions()).hasSize(1);
  }
}
