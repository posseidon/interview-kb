package io.github.posseidon.knowledgebase.it.interview.search;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.VectorStoreIds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared semantic-search primitive: embeds a free-text query, runs similarity search against
 * {@code vector_store}, and re-hydrates the matching {@link Question} rows — in similarity-ranked
 * order, since {@link QuestionRepository#findAllById} does not preserve input order.
 */
@Component
public class SemanticSearchService {

  private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

  /**
   * Default relevance cutoff shared by every caller of {@link #search(String, int)}.
   */
  public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.1f; // Lowered the threshold

  private final VectorStore vectorStore;
  private final QuestionRepository questionRepository;
  private final ChatClient quizChatClient;

  public SemanticSearchService(VectorStore vectorStore, QuestionRepository questionRepository,
      ChatClient quizChatClient) {
    this.vectorStore = vectorStore;
    this.questionRepository = questionRepository;
    this.quizChatClient = quizChatClient;
  }

  public List<Question> search(String query, int topK) {
    return search(query, topK, DEFAULT_SIMILARITY_THRESHOLD);
  }

  @Transactional(readOnly = true)
  public List<Question> search(String query, int topK, float similarityThreshold) {
    List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
        .query(query).topK(topK).similarityThreshold(similarityThreshold).build());

    List<UUID> orderedIds = docs.stream()
        .map(Document::getId)
        .flatMap(id -> VectorStoreIds.parse(id).stream())
        .toList();

    Map<UUID, Question> byId = new LinkedHashMap<>();
    for (Question question : questionRepository.findAllById(orderedIds)) {
      byId.put(question.getId(), question);
    }

    return orderedIds.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Like {@link #search(String, int)}, but first asks the model to expand a vague/broad topic
   * query (e.g. "parallelism") into concrete related terms (e.g. "async, synchronized, deadlock,
   * CompletableFuture, threads") drawing on its own general knowledge — so the caller doesn't need
   * to already know the vocabulary their questions are actually phrased with before a raw
   * embedding similarity search would find them. Falls back to the unexpanded query if the
   * expansion call fails.
   */
  public List<Question> searchExpandingQuery(String query, int topK) {
    return search(expandQuery(query), topK, DEFAULT_SIMILARITY_THRESHOLD);
  }

  private String expandQuery(String query) {
    String prompt = """
        A user is searching a technical interview question-and-answer bank with this query:
        "%s"

        List 8-12 concrete, closely related technical terms, concepts, APIs, or mechanisms a
        developer would use when phrasing interview questions on this topic. Assume the user does
        not already know this terminology themselves. Return ONLY a comma-separated list of terms
        — no explanation, no preamble, no numbering.
        """.formatted(query);
    try {
      String expansion = quizChatClient.prompt().user(prompt).call().content();
      if (expansion == null || expansion.isBlank()) {
        return query;
      }
      return query + " " + expansion.strip();
    } catch (RuntimeException e) {
      log.warn("Query expansion failed for \"{}\"; searching with the raw query instead", query,
          e);
      return query;
    }
  }
}