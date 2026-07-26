package io.github.posseidon.knowledgebase.it.interview.classification;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;

/**
 * Structured output target for a single skill-level classification call — one object per
 * question, not a list, since each question gets its own model call. {@code rationale} is the
 * model's 1-3 sentence justification (which skill(s) and criteria drove the decision) — kept only
 * for logging/debugging, not persisted alongside {@link io.github.posseidon.knowledgebase.it.interview.domain.question.Question#getLevel()}.
 */
public record QuestionLevelClassification(SkillLevel level, String rationale) {

}
