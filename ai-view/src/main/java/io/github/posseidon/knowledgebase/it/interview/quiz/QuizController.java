package io.github.posseidon.knowledgebase.it.interview.quiz;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.web.SessionKeys;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/questions")
public class QuizController {

  private final QuestionRepository questionRepository;
  private final QuestionQuizService quizService;
  private final QuizGenerationStore quizStore;

  public QuizController(QuestionRepository questionRepository, QuestionQuizService quizService,
      QuizGenerationStore quizStore) {
    this.questionRepository = questionRepository;
    this.quizService = quizService;
    this.quizStore = quizStore;
  }

  @Transactional(readOnly = true)
  @PostMapping("/{id}/quiz")
  public String generateQuiz(@PathVariable UUID id, HttpSession session) {
    Question question = questionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    List<String> answerContents = question.getAnswers().stream()
        .map(Answer::getContent)
        .toList();
    if (!answerContents.isEmpty()) {
      quizService.generateAsync(SessionKeys.forId(session, id), question.getContent(),
          answerContents);
    }
    return "redirect:/questions/" + id;
  }

  @GetMapping(value = "/{id}/quiz/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public QuizStatusView quizStatus(@PathVariable UUID id, HttpSession session) {
    String storeKey = SessionKeys.forId(session, id);
    Optional<String> step = quizStore.currentStep(storeKey);
    return new QuizStatusView(step.isPresent(), step.orElse(null),
        quizStore.partialQuestions(storeKey), quizStore.targetCount(storeKey));
  }
}
