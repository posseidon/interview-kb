package io.github.posseidon.knowledgebase.it.interview.interview;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Generates candidate-interview questions (with ideal answers, evaluation flags, and follow-up
 * probes) grounded in the Q&A pairs retrieved for a search query. Mirrors
 * {@link QuestionQuizService}'s async/poll shape and structured-output handling.
 *
 * <p>The system prompt lives in {@code src/main/resources/prompts/interview-system-prompt.st},
 * not in this class — edit that file and restart the app to refine question quality.
 */
@Service
public class QuestionInterviewService {

  private static final Logger log = LoggerFactory.getLogger(QuestionInterviewService.class);
  private static final Set<String> VALID_TYPES = Set.of("answer", "coding");
  // Matches a bare UUID regardless of how the model wraps it — "[id]", "<id>", "`id`", trailing
  // punctuation, etc. — since only the UUID substring itself needs to match validIds.
  private static final Pattern UUID_PATTERN = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Set<Character> BULLET_MARKERS = Set.of('-', '*', '•', '‣');
  private final ChatClient quizChatClient;
  private final InterviewGenerationStore store;
  private final Resource interviewSystemPromptResource;

  public QuestionInterviewService(ChatClient quizChatClient, InterviewGenerationStore store,
      @Value("classpath:prompts/interview-system-prompt.st") Resource interviewSystemPromptResource) {
    this.quizChatClient = quizChatClient;
    this.store = store;
    this.interviewSystemPromptResource = interviewSystemPromptResource;
  }

  private static boolean isBulletLine(String line) {
    return !line.isEmpty() && BULLET_MARKERS.contains(line.charAt(0));
  }

  private static String stripBulletMarker(String line) {
    return isBulletLine(line) ? line.substring(1).strip() : line;
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }

  public void generateAsync(String storeKey, List<Question> questions,
      String seniority, int count) {
    Thread.ofVirtual().name("interview-" + storeKey)
        .start(() -> generateAndStore(storeKey, questions, seniority, count));
  }

  private void generateAndStore(String storeKey, List<Question> questions,
      String seniority, int count) {
    store.begin(storeKey);
    try {
      List<InterviewQuestion> interviewSet = generate(storeKey, questions, seniority, count,
          store.askedQuestions(storeKey));
      store.complete(storeKey, interviewSet);
    } finally {
      store.clearPending(storeKey);
    }
  }

  public List<InterviewQuestion> generate(String storeKey, List<Question> questions,
      String seniority, int count, List<String> alreadyAsked) {
    if (questions == null || questions.isEmpty()) {
      return List.of();   // never call the model with no source
    }

    long start = System.nanoTime();
    store.step(storeKey, "Building source material from " + questions.size() + " question"
        + (questions.size() == 1 ? "" : "s") + "…");
    long stepStart = System.nanoTime();
    // ONLY question text + IDs go into context — this is the main latency win.
    String source = questions.stream()
        .map(q -> "[" + q.getId() + "] " + q.getContent())
        .collect(Collectors.joining("\n"));
    log.info("[interview-timing] key={} step=buildSource durationMs={} questionCount={} "
            + "sourceChars={}",
        storeKey, elapsedMs(stepStart), questions.size(), source.length());

    stepStart = System.nanoTime();
    String excluded = alreadyAsked.isEmpty() ? "None."
        : alreadyAsked.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));

    store.step(storeKey, "Composing the prompt…");
    // Plain concatenation, NOT .param(...) — question text still has braces/backticks that
    // Spring AI's StringTemplate renderer would choke on.
    String userText = """
        Generate %d interview questions for a %s candidate from these source questions.

        SOURCE QUESTIONS:
        %s

        ALREADY-ASKED (do not repeat or re-probe):
        %s
        """.formatted(count, seniority, source, excluded);
    log.info("[interview-timing] key={} step=composePrompt durationMs={} alreadyAskedCount={} "
            + "promptChars={}",
        storeKey, elapsedMs(stepStart), alreadyAsked.size(), userText.length());

    stepStart = System.nanoTime();
    String md = quizChatClient.prompt()
        .system(spec -> spec.text(interviewSystemPromptResource))
        .user(userText)
        .call()
        .content();                 // raw Markdown — no schema injected
    log.info("[interview-timing] key={} step=llmCall durationMs={} responseChars={}",
        storeKey, elapsedMs(stepStart), md == null ? 0 : md.length());
    store.step(storeKey, "The model has answered.");

    if (md == null || md.isBlank()) {
      log.info("[interview-timing] key={} step=TOTAL durationMs={} outcome=emptyResponse",
          storeKey, elapsedMs(start));
      throw new IllegalStateException("Empty LLM response.");
    }

    store.step(storeKey, "Validating the answer and creating interview questions…");
    stepStart = System.nanoTime();
    Set<String> validIds = questions.stream()
        .map(q -> q.getId().toString())   // normalize UUID -> canonical lowercase string
        .collect(Collectors.toSet());
    List<InterviewQuestion> result = parseAndValidate(md, validIds);
    log.info("[interview-timing] key={} step=validate durationMs={} parsedQuestionCount={}",
        storeKey, elapsedMs(stepStart), result.size());

    log.info("[interview-timing] key={} step=TOTAL durationMs={}", storeKey, elapsedMs(start));
    return result;
  }

  public List<InterviewQuestion> parseAndValidate(String md, Set<String> validIds) {
    List<InterviewQuestion> out = new ArrayList<>();

    for (String block : md.split("(?m)^###\\s+Question.*$")) {
      if (block.isBlank()) {
        continue;
      }

      String question = null, type = null;
      List<String> sources = new ArrayList<>();
      List<String> followUps = new ArrayList<>();
      boolean inFollowUps = false;

      for (String raw : block.lines().toList()) {
        String line = raw.strip();
        if (line.isEmpty()) {
          continue;
        }
        if (line.startsWith("Q:")) {
          question = line.substring(2).strip();
          inFollowUps = false;
        } else if (line.startsWith("Type:")) {
          type = line.substring(5).strip().toLowerCase();
          inFollowUps = false;
        } else if (line.startsWith("Sources:")) {
          inFollowUps = false;
          Matcher idMatcher = UUID_PATTERN.matcher(line.substring(8));
          while (idMatcher.find()) {
            sources.add(idMatcher.group().toLowerCase());
          }
        } else if (line.startsWith("Follow-ups:")) {
          inFollowUps = true;
          // Some models inline the first probe on the header line instead of a bullet below it.
          String inline = stripBulletMarker(line.substring("Follow-ups:".length()).strip());
          if (!inline.isEmpty()) {
            followUps.add(inline);
          }
        }
        // Bullet marker varies by model: "-", "*", and "•" all show up in practice.
        else if (inFollowUps && isBulletLine(line)) {
          followUps.add(stripBulletMarker(line));
        }
      }

      if (question == null || question.isBlank()) {
        continue;
      }
      if (type == null || !VALID_TYPES.contains(type)) {
        continue;
      }
      if (sources.size() != 1) {
        continue;   // exactly one source required — no cross-topic multi-source mashups
      }
      if (!validIds.containsAll(sources)) {
        continue;   // the cited id must be a real input id
      }

      out.add(new InterviewQuestion(question, type, sources, followUps));
    }
    return out;
  }
}
