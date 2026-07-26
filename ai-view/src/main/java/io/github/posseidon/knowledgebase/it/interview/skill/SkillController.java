package io.github.posseidon.knowledgebase.it.interview.skill;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.interview.InterviewGenerationStore;
import io.github.posseidon.knowledgebase.it.interview.interview.QuestionInterviewService;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.web.GenerationStatusView;
import io.github.posseidon.knowledgebase.it.interview.web.QuestionCountBounds;
import io.github.posseidon.knowledgebase.it.interview.web.SessionKeys;
import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * Search + detail pages for skills, and interview-question generation scoped to one skill. Reuses
 * {@link QuestionInterviewService}/{@link InterviewGenerationStore} as-is — they already operate on
 * a generic {@code List<Question>} and an opaque store key, with no idea whether the question set
 * came from a semantic search (see {@code question.QuestionController}) or, as here, a skill tag
 * lookup.
 */
@Controller
@RequestMapping("/skills")
public class SkillController {

  private static final int SEARCH_LIMIT = 30;
  private static final int SKILL_QUESTION_LIMIT = 30;

  private final SkillRepository skillRepository;
  private final QuestionRepository questionRepository;
  private final QuestionInterviewService interviewService;
  private final InterviewGenerationStore interviewStore;

  public SkillController(
      SkillRepository skillRepository,
      QuestionRepository questionRepository,
      QuestionInterviewService interviewService,
      InterviewGenerationStore interviewStore) {
    this.skillRepository = skillRepository;
    this.questionRepository = questionRepository;
    this.interviewService = interviewService;
    this.interviewStore = interviewStore;
  }

  private static List<SkillResultItem> toResultItems(List<Skill> skills) {
    return skills.stream()
        .sorted(Comparator.comparing(Skill::getName))
        .map(s -> new SkillResultItem(s.getId(), s.getName(), s.getDescription()))
        .toList();
  }

  private List<Question> questionsFor(UUID id) {
    return this.questionRepository.findBySkillId(id, PageRequest.of(0, SKILL_QUESTION_LIMIT));
  }

  private static String criteriaFor(Skill skill, SkillLevel level) {
    String criteria = switch (level) {
      case NOVICE -> skill.getNoviceCriteria();
      case INTERMEDIATE -> skill.getIntermediateCriteria();
      case ADVANCED -> skill.getAdvancedCriteria();
      case EXPERT -> skill.getExpertCriteria();
    };
    return criteria == null ? "" : criteria;
  }

  /**
   * All four levels' criteria at once — the view switches between them client-side, with no extra
   * request per level selection.
   */
  private static Map<SkillLevel, String> criteriaByLevel(Skill skill) {
    Map<SkillLevel, String> criteria = new EnumMap<>(SkillLevel.class);
    for (SkillLevel level : SkillLevel.values()) {
      criteria.put(level, criteriaFor(skill, level));
    }
    return criteria;
  }

  @GetMapping
  public String search(@RequestParam(required = false) String q, Model model) {
    boolean hasQuery = q != null && !q.isBlank();
    List<SkillResultItem> results = hasQuery
        ? toResultItems(skillRepository.search(q, SEARCH_LIMIT))
        : List.of();
    model.addAttribute("query", q == null ? "" : q);
    model.addAttribute("hasMatches", !results.isEmpty());
    model.addAttribute("results", results);
    return "skill/search";
  }

  @Transactional(readOnly = true)
  @GetMapping("/{id}")
  public String detail(@PathVariable UUID id, HttpSession session, Model model) {
    Skill skill = skillRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    int questionCount = questionsFor(id).size();

    model.addAttribute("skill", skill);
    model.addAttribute("hasQuestions", questionCount > 0);
    model.addAttribute("questionCount", questionCount);
    model.addAttribute("levels", SkillLevel.values());
    model.addAttribute("criteriaByLevel", criteriaByLevel(skill));
    model.addAttribute("interviewSet",
        interviewStore.result(SessionKeys.forId(session, id)).orElse(null));
    return "skill/skill-detail";
  }

  @Transactional(readOnly = true)
  @PostMapping("/{id}/interview-questions")
  public String generateInterviewQuestions(@PathVariable UUID id,
      @RequestParam(defaultValue = "INTERMEDIATE") String seniority,
      @RequestParam(defaultValue = "5") int count, HttpSession session) {
    List<Question> questions = questionsFor(id);
    if (!questions.isEmpty()) {
      int clampedCount = Math.clamp(count, QuestionCountBounds.MIN, QuestionCountBounds.MAX);
      interviewService.generateAsync(SessionKeys.forId(session, id), questions, seniority,
          clampedCount);
    }
    return "redirect:/skills/" + id;
  }

  @GetMapping(value = "/{id}/interview-questions/status",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public GenerationStatusView interviewQuestionsStatus(@PathVariable UUID id,
      HttpSession session) {
    return interviewStore.currentStep(SessionKeys.forId(session, id))
        .map(step -> new GenerationStatusView(true, step))
        .orElseGet(() -> new GenerationStatusView(false, null));
  }

  public record SkillResultItem(UUID id, String name, String description) {

  }
}
