package io.github.posseidon.knowledgebase.it.interview.question;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionEditService {

  private final QuestionRepository questionRepository;
  private final AnswerRepository answerRepository;

  public QuestionEditService(QuestionRepository questionRepository,
      AnswerRepository answerRepository) {
    this.questionRepository = questionRepository;
    this.answerRepository = answerRepository;
  }

  @Transactional
  public void updateQuestionContent(UUID questionId, String content) {
      if (content == null || content.isBlank()) {
          return;
      }
    Question question = findQuestion(questionId);
    String stripped = content.strip();
    String newHash = ContentHash.sha256(stripped);
    if (!newHash.equals(question.getContentHash())) {
      question.setContent(stripped);
      question.setContentHash(newHash);
      question.setUpdatedAt(Instant.now());
    }
  }

  private Question findQuestion(UUID id) {
    return questionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @Transactional
  public void addAnswer(UUID questionId, String content) {
      if (content == null || content.isBlank()) {
          return;
      }
    Question question = findQuestion(questionId);
    String stripped = content.strip();
    answerRepository.save(new Answer(question, stripped, ContentHash.sha256(stripped), "human"));
  }

  @Transactional
  public void updateAnswer(UUID answerId, String content) {
      if (content == null || content.isBlank()) {
          return;
      }
    Answer answer = answerRepository.findById(answerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String stripped = content.strip();
    answer.setContent(stripped);
    answer.setContentHash(ContentHash.sha256(stripped));
  }
}
