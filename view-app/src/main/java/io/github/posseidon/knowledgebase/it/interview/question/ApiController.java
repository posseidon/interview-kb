package io.github.posseidon.knowledgebase.it.interview.question;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

  private final QuestionRepository questionRepository;

  public ApiController(QuestionRepository questionRepository) {
    this.questionRepository = questionRepository;
  }

  @Transactional(readOnly = true)
  @GetMapping("/questions/unanswered")
  public List<UnansweredQuestion> unanswered() {
    return questionRepository.findUnansweredNonImpl().stream()
        .map(q -> new UnansweredQuestion(
            q.getId(),
            q.getContent(),
            q.getSkills().stream().map(Skill::getName).toList()
        ))
        .toList();
  }

  public record UnansweredQuestion(UUID id, String content, List<String> skills) {

  }
}
