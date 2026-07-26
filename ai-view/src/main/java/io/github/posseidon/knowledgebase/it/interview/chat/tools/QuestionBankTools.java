package io.github.posseidon.knowledgebase.it.interview.chat.tools;

import io.github.posseidon.knowledgebase.it.interview.chat.ChatProgressStore;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.search.SemanticSearchService;
import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, grounding-data tools for the chat agent. Each method fetches facts from the knowledge
 * base; the LLM is responsible for turning them into prose/quiz content — no tool here generates or
 * fabricates text on its own.
 */
@Component
@Transactional(readOnly = true)
public class QuestionBankTools {

  private static final int SNIPPET_LEN = 400;
  private static final int DEFAULT_LIMIT = 8;
  private static final int JOB_MATCH_LIMIT = 15;
  private static final int QUIZ_SERIES_LIMIT = 20;
  private static final int SKILL_MATCH_LIMIT = 3;

  private final QuestionRepository questionRepository;
  private final SkillRepository skillRepository;
  private final SemanticSearchService semanticSearchService;
  private final ChatProgressStore progressStore;

  public QuestionBankTools(QuestionRepository questionRepository, SkillRepository skillRepository,
      SemanticSearchService semanticSearchService, ChatProgressStore progressStore) {
    this.questionRepository = questionRepository;
    this.skillRepository = skillRepository;
    this.semanticSearchService = semanticSearchService;
    this.progressStore = progressStore;
  }

  @Tool(description = "Search the interview question bank for questions about a given topic or "
      + "skill (e.g. 'Kafka', 'Java concurrency'). Optionally filter by experience level.")
  public List<RetrievedQuestion> searchQuestionsByTopic(
      @ToolParam(description = "Topic or skill name to search for") String topic,
      @ToolParam(description = "Experience level: NOVICE, INTERMEDIATE, ADVANCED, or EXPERT. "
          + "Omit to include all levels.", required = false) String level) {
    progressStore.step("Searching questions about \"" + topic + "\"…");
    List<Skill> skills = skillRepository.search(topic, SKILL_MATCH_LIMIT);
    if (skills.isEmpty()) {
      return List.of();
    }
    List<Question> questions = new ArrayList<>();
    for (Skill skill : skills) {
      questions.addAll(level == null || level.isBlank()
          ? questionRepository.findBySkillId(skill.getId(), PageRequest.of(0, DEFAULT_LIMIT))
          : questionRepository.findBySkillIdAndLevel(skill.getId(),
              level.toUpperCase(Locale.ROOT)));
    }
    return toRetrieved(questions.stream().distinct().limit(DEFAULT_LIMIT).toList());
  }

  @Tool(description = "Answer a factual question using only the human-written answers stored in "
      + "the knowledge base. Returns the most semantically relevant questions and their answers.")
  public List<RetrievedQuestion> answerFromKnowledgeBase(
      @ToolParam(description = "The user's factual question") String question) {
    progressStore.step("Looking through the knowledge base for an answer…");
    return toRetrieved(semanticSearchService.search(question, DEFAULT_LIMIT));
  }

  @Tool(description = "Given a full job description, find interview questions relevant to the "
      + "skills and technologies it mentions.")
  public List<RetrievedQuestion> matchJobDescription(
      @ToolParam(description = "The full job description text") String jobDescription) {
    progressStore.step("Matching the job description against existing questions…");
    return toRetrieved(
        semanticSearchService.search(jobDescription, JOB_MATCH_LIMIT));
  }

  @Tool(description = "Fetch grounding questions and answers for building a quiz on one named "
      + "skill, optionally at a specific experience level.")
  public List<RetrievedQuestion> generateQuiz(
      @ToolParam(description = "Skill name, e.g. 'Spring Boot'") String skillName,
      @ToolParam(description = "Experience level: NOVICE, INTERMEDIATE, ADVANCED, or EXPERT. "
          + "Omit to include all levels.", required = false) String level) {
    progressStore.step("Building a quiz on \"" + skillName + "\"…");
    return searchQuestionsByTopic(skillName, level);
  }

  @Tool(description = "Given a project or position description, fetch grounding questions across "
      + "all the skills it touches on — each result carries its own skill tags — so a series of "
      + "quizzes (one per relevant skill) can be built from them.")
  public List<RetrievedQuestion> generateQuizSeries(
      @ToolParam(description = "The project or position description text")
      String positionDescription) {
    progressStore.step("Building a quiz series from the position description…");
    return toRetrieved(
        semanticSearchService.search(positionDescription, QUIZ_SERIES_LIMIT));
  }

  private List<RetrievedQuestion> toRetrieved(List<Question> questions) {
    return questions.stream()
        .map(q -> new RetrievedQuestion(
            q.getId(),
            Markdown.toSnippet(q.getContent(), SNIPPET_LEN),
            q.getSkills().stream().map(Skill::getName).toList(),
            q.getAnswers().stream()
                .map(a -> Markdown.toSnippet(a.getContent(), SNIPPET_LEN))
                .toList()))
        .toList();
  }
}
