package io.github.posseidon.knowledgebase.it.interview.question;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.interview.InterviewGenerationStore;
import io.github.posseidon.knowledgebase.it.interview.quiz.QuizGenerationStore;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.search.SemanticSearchService;
import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import io.github.posseidon.knowledgebase.it.interview.web.SessionKeys;
import jakarta.servlet.http.HttpSession;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Search + detail pages for questions. Quiz generation lives in {@code quiz.QuizController};
 * interview-question generation lives in {@code interview.InterviewController} — this class only
 * reads their stores to populate the page model.
 */
@Controller
@RequestMapping("/questions")
public class QuestionController {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());
  private static final int SEARCH_TOP_K = 30;

  private final SemanticSearchService semanticSearchService;
  private final QuestionRepository questionRepository;
  private final QuestionMapper questionMapper;
  private final QuizGenerationStore quizStore;
  private final InterviewGenerationStore interviewStore;

  public QuestionController(SemanticSearchService semanticSearchService,
      QuestionRepository questionRepository, QuestionMapper questionMapper,
      QuizGenerationStore quizStore, InterviewGenerationStore interviewStore) {
    this.semanticSearchService = semanticSearchService;
    this.questionRepository = questionRepository;
    this.questionMapper = questionMapper;
    this.quizStore = quizStore;
    this.interviewStore = interviewStore;
  }

  private static List<QuestionResultItem> toResultItems(List<Question> questions) {
    AtomicInteger counter = new AtomicInteger(0);
    return questions.stream()
        .map(q -> new QuestionResultItem(q.getId(), q.isRequiresImpl(),
            Markdown.toHtml(q.getContent()), counter.incrementAndGet()))
        .toList();
  }

  @GetMapping
  public String search(@RequestParam(required = false) String q, HttpSession session,
      Model model) {
    boolean hasQuery = q != null && !q.isBlank();
    List<QuestionResultItem> results = hasQuery
        ? toResultItems(semanticSearchService.searchExpandingQuery(q, SEARCH_TOP_K))
        : List.of();
    model.addAttribute("query", q == null ? "" : q);
    model.addAttribute("hasMatches", !results.isEmpty());
    model.addAttribute("totalCount", results.size());
    model.addAttribute("results", results);
    model.addAttribute("interviewSet", hasQuery
        ? interviewStore.result(SessionKeys.forId(session, q)).orElse(null)
        : null);
    model.addAttribute("levels", SkillLevel.values());
    return "question/search";
  }

  @Transactional(readOnly = true)
  @GetMapping("/{id}")
  public String detail(@PathVariable UUID id, HttpSession session, Model model) {
    Question question = questionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    List<AnswerDetail> answerDetails = question.getAnswers().stream()
        .sorted(Comparator.comparing(Answer::getCreatedAt))
        .map(a -> new AnswerDetail(a.getId(), a.getSource(), Markdown.toHtml(a.getContent())))
        .toList();

    model.addAttribute("question", questionMapper.toView(question));
    model.addAttribute("questionContentHtml", Markdown.toHtml(question.getContent()));
    model.addAttribute("answerDetails", answerDetails);
    model.addAttribute("createdAtDisplay", DATE_FMT.format(question.getCreatedAt()));
    model.addAttribute("quiz", quizStore.result(SessionKeys.forId(session, id)).orElse(null));
    return "question/question-detail";
  }

  public record QuestionResultItem(UUID id, boolean requiresImpl, String contentHtml,
                                   int number) {

  }

  public record AnswerDetail(UUID id, String source, String htmlContent) {

  }
}
