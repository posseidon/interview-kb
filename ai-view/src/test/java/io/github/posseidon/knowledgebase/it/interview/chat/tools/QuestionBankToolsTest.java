package io.github.posseidon.knowledgebase.it.interview.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.chat.ChatProgressStore;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.search.SemanticSearchService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class QuestionBankToolsTest {

  private QuestionRepository questionRepository;
  private SkillRepository skillRepository;
  private SemanticSearchService semanticSearchService;
  private QuestionBankTools tools;

  private static Question questionWith(String content, Skill skill, String... answers) {
    Question question = new Question(content, "hash-" + content.hashCode());
    question.setId(UUID.randomUUID());
    if (skill != null) {
      question.getSkills().add(skill);
    }
    for (String a : answers) {
      question.getAnswers().add(new Answer(question, a, "hash-" + a.hashCode(), "human"));
    }
    return question;
  }

  @BeforeEach
  void setUp() {
    questionRepository = mock(QuestionRepository.class);
    skillRepository = mock(SkillRepository.class);
    semanticSearchService = mock(SemanticSearchService.class);
    tools = new QuestionBankTools(questionRepository, skillRepository, semanticSearchService,
        new ChatProgressStore());
  }

  @Test
  void searchQuestionsByTopicReturnsEmptyWhenNoSkillMatches() {
    when(skillRepository.search(eq("nonexistent"), anyInt())).thenReturn(List.of());

    List<RetrievedQuestion> result = tools.searchQuestionsByTopic("nonexistent", null);

    assertThat(result).isEmpty();
  }

  @Test
  void searchQuestionsByTopicUsesAllLevelsWhenLevelOmitted() {
    Skill kafka = new Skill("Kafka", "Kafka", null, null, null);
    kafka.setId(UUID.randomUUID());
    when(skillRepository.search(eq("Kafka"), anyInt())).thenReturn(List.of(kafka));
    Question question = questionWith("What is a partition?", kafka, "It's a unit of parallelism.");
    when(questionRepository.findBySkillId(eq(kafka.getId()), any(Pageable.class)))
        .thenReturn(List.of(question));

    List<RetrievedQuestion> result = tools.searchQuestionsByTopic("Kafka", null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(question.getId());
    assertThat(result.get(0).skills()).containsExactly("Kafka");
    assertThat(result.get(0).answers()).containsExactly("It's a unit of parallelism.");
    verify(questionRepository, never()).findBySkillIdAndLevel(any(), any());
  }

  @Test
  void searchQuestionsByTopicFiltersByLevelWhenProvided() {
    Skill java = new Skill("Java", "Java", null, null, null);
    java.setId(UUID.randomUUID());
    when(skillRepository.search(eq("Java"), anyInt())).thenReturn(List.of(java));
    Question question = questionWith("Explain generics", java);
    when(questionRepository.findBySkillIdAndLevel(java.getId(), "ADVANCED"))
        .thenReturn(List.of(question));

    List<RetrievedQuestion> result = tools.searchQuestionsByTopic("Java", "advanced");

    assertThat(result).hasSize(1);
    verify(questionRepository).findBySkillIdAndLevel(java.getId(), "ADVANCED");
  }

  @Test
  void answerFromKnowledgeBaseUsesSemanticSearchService() {
    Question question = questionWith("How does ordering work?", null, "Per-partition ordering.");
    when(semanticSearchService.search(eq("How does Kafka order messages?"), anyInt()))
        .thenReturn(List.of(question));

    List<RetrievedQuestion> result = tools.answerFromKnowledgeBase(
        "How does Kafka order messages?");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).answers()).containsExactly("Per-partition ordering.");
  }

  @Test
  void answerFromKnowledgeBaseReturnsEmptyWhenNoMatches() {
    when(semanticSearchService.search(eq("anything"), anyInt()))
        .thenReturn(List.of());

    List<RetrievedQuestion> result = tools.answerFromKnowledgeBase("anything");

    assertThat(result).isEmpty();
  }

  @Test
  void matchJobDescriptionRunsSemanticSearch() {
    when(semanticSearchService.search(eq("Looking for a Java backend dev"), anyInt()))
        .thenReturn(List.of());

    List<RetrievedQuestion> result = tools.matchJobDescription("Looking for a Java backend dev");

    assertThat(result).isEmpty();
    verify(semanticSearchService).search(eq("Looking for a Java backend dev"), anyInt());
  }

  @Test
  void generateQuizDelegatesToTopicSearch() {
    Skill python = new Skill("Python", "Python", null, null, null);
    python.setId(UUID.randomUUID());
    when(skillRepository.search(eq("Python"), anyInt())).thenReturn(List.of(python));
    Question question = questionWith("What is a decorator?", python);
    when(questionRepository.findBySkillIdAndLevel(python.getId(), "NOVICE"))
        .thenReturn(List.of(question));

    List<RetrievedQuestion> result = tools.generateQuiz("Python", "NOVICE");

    assertThat(result).hasSize(1);
  }

  @Test
  void generateQuizSeriesRunsSemanticSearchAcrossSkills() {
    Skill java = new Skill("Java", "Java", null, null, null);
    java.setId(UUID.randomUUID());
    Question q1 = questionWith("Generics?", java);
    when(semanticSearchService.search(eq("Senior Java backend engineer"), anyInt()))
        .thenReturn(List.of(q1));

    List<RetrievedQuestion> result = tools.generateQuizSeries("Senior Java backend engineer");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).skills()).containsExactly("Java");
  }
}
