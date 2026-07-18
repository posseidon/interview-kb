package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single-question content mutations (overwrite, add answer, overwrite answer) for the
 * {@code /ingest/question/**} endpoints. Keeps {@code vector_store} in sync with question content
 * changes, unlike the view-app edit path this replaces.
 */
@Service
public class QuestionContentService {

  private final QuestionRepository questionRepository;
  private final AnswerRepository answerRepository;
  private final VectorStore vectorStore;

  public QuestionContentService(QuestionRepository questionRepository,
      AnswerRepository answerRepository, VectorStore vectorStore) {
    this.questionRepository = questionRepository;
    this.answerRepository = answerRepository;
    this.vectorStore = vectorStore;
  }

  @Transactional
  public void overwriteContent(UUID questionId, String content) {
    requireContent(content);
    Question question = findQuestion(questionId);
    String stripped = content.strip();
    String newHash = ContentHash.sha256(stripped);
    if (newHash.equals(question.getContentHash())) {
      return;
    }
    question.setContent(stripped);
    question.setContentHash(newHash);
    question.setUpdatedAt(Instant.now());
    vectorStore.delete(List.of(question.getId().toString()));
    vectorStore.add(List.of(QuestionDocuments.toDocument(question)));
  }

  @Transactional
  public void addAnswer(UUID questionId, String content) {
    requireContent(content);
    Question question = findQuestion(questionId);
    String stripped = content.strip();
    answerRepository.save(new Answer(question, stripped, ContentHash.sha256(stripped), "human"));
  }

  @Transactional
  public void overwriteAnswer(UUID answerId, String content) {
    requireContent(content);
    Answer answer = answerRepository.findById(answerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String stripped = content.strip();
    answer.setContent(stripped);
    answer.setContentHash(ContentHash.sha256(stripped));
  }

  private void requireContent(String content) {
    if (content == null || content.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content must not be blank");
    }
  }

  private Question findQuestion(UUID id) {
    return questionRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
