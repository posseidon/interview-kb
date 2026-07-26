package io.github.posseidon.knowledgebase.it.interview.web;

/**
 * Valid range for a caller-requested count of generated questions (quiz or interview).
 */
public final class QuestionCountBounds {

  public static final int MIN = 3;
  public static final int MAX = 10;

  private QuestionCountBounds() {
  }
}
