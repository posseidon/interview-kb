package io.github.posseidon.knowledgebase.it.interview.web;

import jakarta.servlet.http.HttpSession;

/**
 * Builds the opaque, session-scoped keys used by the various generation-state stores.
 */
public final class SessionKeys {

  private SessionKeys() {
  }

  public static String forId(HttpSession session, Object id) {
    return session.getId() + ":" + id;
  }
}
