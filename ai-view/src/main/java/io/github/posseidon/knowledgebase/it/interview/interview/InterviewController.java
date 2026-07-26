package io.github.posseidon.knowledgebase.it.interview.interview;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.search.SemanticSearchService;
import io.github.posseidon.knowledgebase.it.interview.web.GenerationStatusView;
import io.github.posseidon.knowledgebase.it.interview.web.QuestionCountBounds;
import io.github.posseidon.knowledgebase.it.interview.web.SessionKeys;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/questions")
public class InterviewController {

  private static final int SEARCH_TOP_K = 30;

  private final SemanticSearchService semanticSearchService;
  private final QuestionInterviewService interviewService;
  private final InterviewGenerationStore interviewStore;

  public InterviewController(SemanticSearchService semanticSearchService,
      QuestionInterviewService interviewService, InterviewGenerationStore interviewStore) {
    this.semanticSearchService = semanticSearchService;
    this.interviewService = interviewService;
    this.interviewStore = interviewStore;
  }

  @Transactional(readOnly = true)
  @PostMapping("/interview-questions")
  public String generateInterviewQuestions(@RequestParam String q,
      @RequestParam(defaultValue = "INTERMEDIATE") String seniority,
      @RequestParam(defaultValue = "5") int count, HttpSession session) {
    List<Question> questions = semanticSearchService.search(q, SEARCH_TOP_K);
    if (!questions.isEmpty()) {
      int clampedCount = Math.clamp(count, QuestionCountBounds.MIN, QuestionCountBounds.MAX);
      interviewService.generateAsync(SessionKeys.forId(session, q), questions, seniority,
          clampedCount);
    }
    return "redirect:/questions?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
  }

  @GetMapping(value = "/interview-questions/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public GenerationStatusView interviewQuestionsStatus(@RequestParam String q,
      HttpSession session) {
    return interviewStore.currentStep(SessionKeys.forId(session, q))
        .map(step -> new GenerationStatusView(true, step))
        .orElseGet(() -> new GenerationStatusView(false, null));
  }
}
