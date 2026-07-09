package io.github.posseidon.knowledgebase.it.interview.mcp;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class SkillQuestionMcpTool {

    private final SkillRepository skillRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public SkillQuestionMcpTool(SkillRepository skillRepository,
                                QuestionRepository questionRepository,
                                QuestionMapper questionMapper) {
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @Tool(name = "find_questions_for_skill",
            description = "Match a skill name (e.g. from a job position description) to the closest skill "
                    + "in the interview knowledge base's catalog, and return its interview questions at the "
                    + "required proficiency level. If no catalog skill reasonably matches, 'found' is false.")
    public SkillQuestionsResult findQuestionsForSkill(
            @ToolParam(description = "Skill name to match, e.g. 'Java', 'Kafka', 'Angular'") String skillName,
            @ToolParam(required = false,
                    description = "Required proficiency level: NOVICE, INTERMEDIATE, ADVANCED, or EXPERT. "
                            + "Defaults to INTERMEDIATE if omitted or not one of these values.") String level) {
        if (skillName == null || skillName.isBlank()) {
            return SkillQuestionsResult.notFound(skillName);
        }

        List<Skill> matches = skillRepository.search(skillName.strip(), 5);
        if (matches.isEmpty()) {
            return SkillQuestionsResult.notFound(skillName);
        }

        Skill skill = matches.get(0);
        SkillLevel resolvedLevel = parseLevel(level);

        List<Question> questions = questionRepository.findBySkillIdAndLevel(skill.getId(), resolvedLevel.name());
        List<QuestionView> views = questions.stream().map(questionMapper::toView).toList();

        return new SkillQuestionsResult(true, skill.getId(), skill.getName(), resolvedLevel.name(), views);
    }

    private static SkillLevel parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return SkillLevel.INTERMEDIATE;
        }
        try {
            return SkillLevel.valueOf(level.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SkillLevel.INTERMEDIATE;
        }
    }

    public record SkillQuestionsResult(boolean found, UUID skillId, String skillName, String level,
                                        List<QuestionView> questions) {
        static SkillQuestionsResult notFound(String skillName) {
            return new SkillQuestionsResult(false, null, skillName, null, List.of());
        }
    }
}
