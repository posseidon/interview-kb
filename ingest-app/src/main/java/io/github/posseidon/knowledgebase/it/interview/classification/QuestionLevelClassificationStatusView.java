package io.github.posseidon.knowledgebase.it.interview.classification;

/**
 * Poll response for the skill-level classification job.
 */
public record QuestionLevelClassificationStatusView(boolean running, long total, long processed,
    long skipped, long failed, String error) {

}
