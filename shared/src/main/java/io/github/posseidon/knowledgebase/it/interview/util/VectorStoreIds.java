package io.github.posseidon.knowledgebase.it.interview.util;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code vector_store} document's id is always a question UUID's string form (see
 * {@code QuestionDocuments}). This centralizes the one place that assumption is parsed back, so a
 * non-UUID id — e.g. a stale or foreign entry — is skipped consistently everywhere a similarity
 * search result is turned back into a question id, instead of each call site re-implementing its
 * own try/catch.
 */
public final class VectorStoreIds {

  private static final Logger log = LoggerFactory.getLogger(VectorStoreIds.class);

  private VectorStoreIds() {
  }

  public static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      log.warn("Skipping vector-store document with non-UUID id: {}", id);
      return Optional.empty();
    }
  }
}
