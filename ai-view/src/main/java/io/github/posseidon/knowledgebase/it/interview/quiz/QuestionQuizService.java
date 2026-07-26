package io.github.posseidon.knowledgebase.it.interview.quiz;

import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.web.QuestionCountBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Generates a multiple-choice quiz grounded in a specific question's own answer content. Takes
 * plain, already-detached {@code String} content rather than a {@code Question} entity: generation
 * runs on a background thread (see {@link #generateAsync}), and a JPA entity's lazy collections
 * can't be touched once the request's Hibernate session — which is thread-bound — has closed.
 *
 * <p>The system prompt itself lives in {@code src/main/resources/prompts/quiz-system-prompt.st},
 * not in this class — edit that file and restart the app to refine quiz quality without touching
 * Java code.
 *
 * <p>Generation is split into two model calls instead of one: the first asks for a single
 * question, which is stored and made visible to pollers immediately (see
 * {@link QuizGenerationStore#appendQuestions}) so the quiz can be started well before the rest is
 * ready; the second asks for the remaining questions as one batch, same as the original
 * single-call design. This keeps token/cost overhead to roughly 2x the reference material's
 * prefill cost (not Nx, one per question) while cutting perceived time-to-first-question from
 * ~10s to ~1-2s — measurements showed decode time (proportional to how much is generated), not
 * input size, dominates call latency, so batching the bulk of the output after an instant first
 * question is the effective lever.
 */
@Service
public class QuestionQuizService {

  private static final Logger log = LoggerFactory.getLogger(QuestionQuizService.class);
  private static final int WORDS_PER_QUESTION = 120;
  private static final int UNBOUNDED_SNIPPET = Integer.MAX_VALUE;
  private static final String NO_PRIOR_QUESTIONS = "(none yet)";
  private static final Random RNG = new Random();

  private final ChatClient quizChatClient;
  private final QuizGenerationStore store;
  private final Resource quizSystemPromptResource;

  public QuestionQuizService(ChatClient quizChatClient, QuizGenerationStore store,
      @Value("classpath:prompts/quiz-system-prompt.st") Resource quizSystemPromptResource) {
    this.quizChatClient = quizChatClient;
    this.store = store;
    this.quizSystemPromptResource = quizSystemPromptResource;
  }

  private static String priorQuestionsText(List<String> priorQuestions) {
    if (priorQuestions.isEmpty()) {
      return NO_PRIOR_QUESTIONS;
    }
    return priorQuestions.stream()
        .map(q -> "- " + q)
        .reduce((a, b) -> a + "\n" + b)
        .orElse(NO_PRIOR_QUESTIONS);
  }

  private static String referenceContent(String questionContent, List<String> answerContents) {
    StringBuilder content = new StringBuilder("Question: ").append(questionContent)
        .append("\n\n");
    int index = 1;
    for (String answer : answerContents) {
      content.append("Answer ").append(index++).append(": ").append(answer).append("\n\n");
    }
    return content.toString();
  }

  private static int questionCount(List<String> answerContents) {
    int totalWords = answerContents.stream()
        .mapToInt(QuestionQuizService::wordCount)
        .sum();
    int computed = (int) Math.ceil(totalWords / (double) WORDS_PER_QUESTION);
    return Math.clamp(computed, QuestionCountBounds.MIN, QuestionCountBounds.MAX);
  }

  private static int wordCount(String markdown) {
    String plain = Markdown.toSnippet(markdown, UNBOUNDED_SNIPPET);
    return plain.isBlank() ? 0 : plain.trim().split("\\s+").length;
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }

  public void generateAsync(String storeKey, String questionContent,
      List<String> answerContents) {
    Thread.ofVirtual().name("quiz-" + storeKey)
        .start(() -> generate(storeKey, questionContent, answerContents));
  }

  private void generate(String storeKey, String questionContent, List<String> answerContents) {
    store.begin(storeKey);
    long start = System.nanoTime();
    try {
      long stepStart = System.nanoTime();
      int questionCount = questionCount(answerContents);
      int totalAnswerChars = answerContents.stream().mapToInt(String::length).sum();
      log.info("[quiz-timing] key={} step=questionCount durationMs={} answerCount={} "
              + "totalAnswerChars={} questionCount={}",
          storeKey, elapsedMs(stepStart), answerContents.size(), totalAnswerChars, questionCount);
      store.targetCount(storeKey, questionCount);

      String reference = referenceContent(questionContent, answerContents);
      List<String> exclusion = new ArrayList<>(store.askedQuestions(storeKey));
      List<QuizQuestion> generated = new ArrayList<>();

      store.step(storeKey, "Generating question 1 of " + questionCount + "…");
      List<QuizQuestion> first = attemptFirstQuestion(storeKey, reference, exclusion);
      if (!first.isEmpty()) {
        generated.addAll(first);
        store.appendQuestions(storeKey, first);
        first.forEach(q -> exclusion.add(q.question()));
      }

      int remaining = questionCount - generated.size();
      if (!first.isEmpty() && remaining > 0) {
        store.step(storeKey, "Generating the remaining " + remaining + " question"
            + (remaining == 1 ? "" : "s") + "…");
        List<QuizQuestion> rest = validate(storeKey, "rest",
            callModel(storeKey, reference, remaining, exclusion, "rest"));
        if (!rest.isEmpty()) {
          generated.addAll(rest);
          store.appendQuestions(storeKey, rest);
        }
      } else if (first.isEmpty()) {
        // The single-question call failed twice in a row — fall back to the original one-shot
        // batch for the whole quiz rather than leaving the user with nothing.
        log.warn("Single-question generation failed for key {}; falling back to a single batch "
            + "call for all {} questions", storeKey, questionCount);
        store.step(storeKey, "Generating " + questionCount + " quiz question"
            + (questionCount == 1 ? "" : "s") + "…");
        List<QuizQuestion> fallback = validate(storeKey, "fallback-full-batch",
            callModel(storeKey, reference, questionCount, exclusion, "fallback-full-batch"));
        generated.addAll(fallback);
        store.appendQuestions(storeKey, fallback);
      }

      Quiz quiz = new Quiz(List.copyOf(generated));
      store.complete(storeKey, quiz);
      log.info("[quiz-timing] key={} step=TOTAL durationMs={} totalValidQuestions={}",
          storeKey, elapsedMs(start), quiz.questions().size());
    } finally {
      store.clearPending(storeKey);
    }
  }

  /**
   * Requests a single question, retrying once (with the same exclusion list) if the model call
   * fails or its output doesn't survive validation. Returns an empty list — never throws — if both
   * attempts fail, letting the caller fall back to a single batch call for the whole quiz.
   */
  private List<QuizQuestion> attemptFirstQuestion(String storeKey, String reference,
      List<String> exclusion) {
    List<QuizQuestion> first = validate(storeKey, "first",
        callModel(storeKey, reference, 1, exclusion, "first"));
    if (!first.isEmpty()) {
      return first;
    }
    log.warn("First-question generation produced nothing usable for key {}; retrying once",
        storeKey);
    return validate(storeKey, "first-retry",
        callModel(storeKey, reference, 1, exclusion, "first-retry"));
  }

  /**
   * Requested as a bare {@code List<GeneratedQuestion>}, not an object-wrapped record: local
   * models reliably emit a JSON array for a list-shaped request but often ignore instructions to
   * wrap it in an object, which breaks deserialization. Never throws — returns an empty list on
   * any model/parse failure.
   */
  private List<GeneratedQuestion> callModel(String storeKey, String reference, int count,
      List<String> exclusion, String callLabel) {
    String userText = """
        Generate %d NEW multiple-choice question%s from the source material below.
        SOURCE MATERIAL:
        %s
        ALREADY-ASKED QUESTIONS (do not repeat or re-test these):
        %s
        """.formatted(count, count == 1 ? "" : "s", reference, priorQuestionsText(exclusion));

    long stepStart = System.nanoTime();
    List<GeneratedQuestion> generatedQuestions;
    boolean callFailed = false;
    try {
      generatedQuestions = quizChatClient.prompt()
          .system(spec -> spec.text(quizSystemPromptResource))
          .user(userText)
          .call()
          .entity(new ParameterizedTypeReference<>() {
          });
    } catch (RuntimeException e) {
      log.warn("Quiz generation model output could not be parsed for key {} (call={})", storeKey,
          callLabel, e);
      generatedQuestions = null;
      callFailed = true;
    }
    log.info("[quiz-timing] key={} step=llmCall call={} durationMs={} failed={} requested={} "
            + "resultCount={} promptChars={}",
        storeKey, callLabel, elapsedMs(stepStart), callFailed, count,
        generatedQuestions == null ? 0 : generatedQuestions.size(), userText.length());
    if (generatedQuestions == null) {
      return List.of();
    }
    if (generatedQuestions.isEmpty()) {
      log.warn("Model returned a valid but empty question list for key {} (call={})", storeKey,
          callLabel);
    }
    return generatedQuestions;
  }

  private List<QuizQuestion> validate(String storeKey, String callLabel,
      List<GeneratedQuestion> raw) {
    long stepStart = System.nanoTime();
    List<QuizQuestion> validated = raw.stream()
        .map(this::toStoredQuestion)
        .flatMap(Optional::stream)
        .toList();
    log.info("[quiz-timing] key={} step=validate call={} durationMs={} rawCount={} validCount={}",
        storeKey, callLabel, elapsedMs(stepStart), raw.size(), validated.size());
    if (!raw.isEmpty() && validated.isEmpty()) {
      log.warn("Model returned {} question(s) for key {} (call={}) but none survived validation "
              + "(bad option count or correctAnswer not matching an option)",
          raw.size(), storeKey, callLabel);
    }
    return validated;
  }

  private Optional<QuizQuestion> toStoredQuestion(GeneratedQuestion g) {
    if (g.options() == null || g.options().size() != 4) {
      return Optional.empty();
    }

    // The correct answer must appear EXACTLY once in options (exact string match).
    List<Integer> matches = new ArrayList<>();
    for (int i = 0; i < g.options().size(); i++) {
      if (g.options().get(i).equals(g.correctAnswer())) {
        matches.add(i);
      }
    }
    if (matches.size() != 1) {
      return Optional.empty(); // absent or ambiguous -> discard
    }

    // Randomize position; recompute where the correct answer landed.
    List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
    Collections.shuffle(order, RNG);
    List<String> shuffled = order.stream().map(g.options()::get).toList();
    int correctIndex = order.indexOf(matches.get(0));

    // Invariant, by construction: shuffled.get(correctIndex).equals(g.correctAnswer())
    return Optional.of(new QuizQuestion(g.question(), shuffled, correctIndex, g.explanation()));
  }
}
