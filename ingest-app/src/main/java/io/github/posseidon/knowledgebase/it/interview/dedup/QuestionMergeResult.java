package io.github.posseidon.knowledgebase.it.interview.dedup;

/**
 * Structured output target for the merge-rephrase call: the single combined question text, kept
 * strictly to the content of the two source questions (see {@code question-merge-system.st}).
 */
record QuestionMergeResult(String mergedQuestion, String rationale) {}
