package io.github.posseidon.knowledgebase.it.interview.question;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public ApiController(QuestionRepository questionRepository,
                         AnswerRepository answerRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
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

    @Transactional
    @PostMapping("/questions/{id}/answers")
    public ResponseEntity<Void> addAnswer(@PathVariable UUID id,
                                          @RequestBody AddAnswerRequest req) {
        if (req.content() == null || req.content().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String source = (req.source() != null && !req.source().isBlank()) ? req.source() : "claude";
        String stripped = req.content().strip();
        answerRepository.save(new Answer(question, stripped, ContentHash.sha256(stripped), source));
        return ResponseEntity.ok().build();
    }

    public record UnansweredQuestion(UUID id, String content, List<String> skills) {}
    public record AddAnswerRequest(String content, String source) {}
}
