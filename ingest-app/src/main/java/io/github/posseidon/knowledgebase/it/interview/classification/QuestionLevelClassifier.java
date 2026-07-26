package io.github.posseidon.knowledgebase.it.interview.classification;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.metrics.LlmTokenMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The single-question skill-level classification call, extracted from
 * {@link QuestionLevelClassificationService} so it can be reused by any caller that needs to
 * classify one question — the whole-table batch job and the ingestion-time auto-merge pass both
 * need exactly this operation, without either one depending on the other's orchestration
 * (progress-tracking for the batch job, merge orchestration for ingestion).
 *
 * <p>Never throws: returns {@link Optional#empty()} if the question has no skills assigned (no
 * criteria to classify against) or the model call/parse fails. Callers decide what "no result"
 * means for their own bookkeeping.
 */
@Component
public class QuestionLevelClassifier {

  private static final Logger log = LoggerFactory.getLogger(QuestionLevelClassifier.class);
  private static final String JOB = "skill_level_classification";

  private final ChatClient chatClient;
  private final MeterRegistry meterRegistry;
  private final LlmTokenMetrics tokenMetrics;
  private final Resource classificationSystemPromptResource;

  public QuestionLevelClassifier(ChatClient chatClient, MeterRegistry meterRegistry,
      LlmTokenMetrics tokenMetrics,
      @Value("classpath:prompts/skill-level-classification-system.st")
      Resource classificationSystemPromptResource) {
    this.chatClient = chatClient;
    this.meterRegistry = meterRegistry;
    this.tokenMetrics = tokenMetrics;
    this.classificationSystemPromptResource = classificationSystemPromptResource;
  }

  private static String buildUserPrompt(Question question) {
    StringBuilder prompt = new StringBuilder();
    for (Skill skill : question.getSkills()) {
      prompt.append("Skill: ").append(skill.getName()).append('\n')
          .append("Description: ").append(skill.getDescription()).append('\n')
          .append("Novice criteria: ").append(skill.getNoviceCriteria()).append('\n')
          .append("Intermediate criteria: ").append(skill.getIntermediateCriteria()).append('\n')
          .append("Advanced criteria: ").append(skill.getAdvancedCriteria()).append('\n')
          .append("Expert criteria: ").append(skill.getExpertCriteria()).append("\n\n");
    }
    prompt.append("Question: ").append(question.getContent());
    return prompt.toString();
  }

  public Optional<QuestionLevelClassification> classify(Question question) {
    if (question.getSkills().isEmpty()) {
      return Optional.empty();
    }

    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      ResponseEntity<ChatResponse, QuestionLevelClassification> result = chatClient.prompt()
          .system(spec -> spec.text(classificationSystemPromptResource))
          .user(buildUserPrompt(question))
          .call()
          .responseEntity(QuestionLevelClassification.class);

      tokenMetrics.record(JOB, result.response());
      QuestionLevelClassification classification = result.entity();
      if (classification == null || classification.level() == null) {
        throw new IllegalStateException("Model returned no classification");
      }
      return Optional.of(classification);
    } catch (RuntimeException e) {
      log.warn("Skill-level classification failed for question {}", question.getId(), e);
      return Optional.empty();
    } finally {
      sample.stop(meterRegistry.timer("ingest.job.call.duration", "job", JOB, "step",
          "classifyQuestion"));
    }
  }
}
