package io.github.posseidon.knowledgebase.it.interview.web;

import io.github.posseidon.knowledgebase.it.interview.ask.AskService;
import io.github.posseidon.knowledgebase.it.interview.domain.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.Tag;
import io.github.posseidon.knowledgebase.it.interview.domain.Topic;
import io.github.posseidon.knowledgebase.it.interview.dto.AskResponse;
import io.github.posseidon.knowledgebase.it.interview.dto.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.ingest.ContentHash;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.TagRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.TopicRepository;
import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HandbookViewController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final TopicRepository topicRepository;
    private final TagRepository tagRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AskService askService;
    private final VectorStore vectorStore;
    private final QuestionMapper questionMapper;

    public HandbookViewController(TopicRepository topicRepository,
                                  TagRepository tagRepository,
                                  QuestionRepository questionRepository,
                                  AnswerRepository answerRepository,
                                  AskService askService,
                                  VectorStore vectorStore,
                                  QuestionMapper questionMapper) {
        this.topicRepository = topicRepository;
        this.tagRepository = tagRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.askService = askService;
        this.vectorStore = vectorStore;
        this.questionMapper = questionMapper;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String scope, Model model) {
        model.addAttribute("scope", scope != null ? scope : "all");
        model.addAttribute("totalQuestions", questionRepository.count());
        return "home";
    }

    @Transactional(readOnly = true)
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String topic,
                         @RequestParam(required = false) String tag,
                         @RequestParam(required = false) String scope,
                         Model model) {
        boolean isAsk = q != null && !q.isBlank();
        boolean isBrowseTag = !isAsk && tag != null && !tag.isBlank();
        List<QuestionView> results;
        String displayQuery;

        if (isAsk) {
            AskResponse resp = askService.ask(q);
            results = resp.sources();
            displayQuery = q;
            model.addAttribute("synthesisBullets", buildSynthesisBullets(results));
        } else if (isBrowseTag) {
            results = questionRepository.findByTagName(tag, PageRequest.of(0, 50))
                    .stream().map(questionMapper::toView).toList();
            displayQuery = tag;
        } else {
            results = questionRepository.findByTopicSlug(topic, PageRequest.of(0, 50))
                    .stream().map(questionMapper::toView).toList();
            displayQuery = topicRepository.findBySlug(topic)
                    .map(Topic::getName).orElse(topic);
        }

        if ("coding".equals(scope)) {
            results = results.stream().filter(QuestionView::requiresImpl).toList();
        } else if ("theory".equals(scope)) {
            results = results.stream().filter(r -> !r.requiresImpl()).toList();
        }

        model.addAttribute("isAsk", isAsk);
        model.addAttribute("query", displayQuery);
        model.addAttribute("hasMatches", !results.isEmpty());
        model.addAttribute("groups", groupByTopic(results));
        return "search";
    }

    @Transactional(readOnly = true)
    @GetMapping("/questions/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<AnswerDetail> answerDetails = question.getAnswers().stream()
                .sorted(Comparator.comparing(Answer::getCreatedAt))
                .map(a -> new AnswerDetail(a.getId(), a.getSource(), a.getContent(),
                        Markdown.toHtml(a.getContent())))
                .toList();

        model.addAttribute("question", questionMapper.toView(question));
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("createdAtDisplay", DATE_FMT.format(question.getCreatedAt()));
        return "detail";
    }

    @Transactional
    @PostMapping("/questions/{id}")
    public String updateQuestion(@PathVariable UUID id, @RequestParam String content) {
        if (content == null || content.isBlank()) return "redirect:/questions/" + id;
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String stripped = content.strip();
        String newHash = ContentHash.sha256(stripped);
        if (!newHash.equals(question.getContentHash())) {
            question.setContent(stripped);
            question.setContentHash(newHash);
            question.setUpdatedAt(Instant.now());
            vectorStore.delete(List.of(id.toString()));
            vectorStore.add(List.of(Document.builder()
                    .id(id.toString()).text(stripped)
                    .metadata(Map.of(
                            "topics", question.getTopics().stream().map(Topic::getSlug).toList(),
                            "tags", question.getTags().stream().map(Tag::getName).toList(),
                            "frequency", question.getFrequency()))
                    .build()));
        }
        return "redirect:/questions/" + id;
    }

    @Transactional
    @PostMapping("/questions/{id}/answers")
    public String addAnswer(@PathVariable UUID id, @RequestParam String content) {
        if (content == null || content.isBlank()) return "redirect:/questions/" + id;
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String stripped = content.strip();
        answerRepository.save(new Answer(question, stripped, ContentHash.sha256(stripped), "human"));
        return "redirect:/questions/" + id;
    }

    @Transactional
    @PostMapping("/questions/{id}/answers/{answerId}")
    public String updateAnswer(@PathVariable UUID id,
                               @PathVariable UUID answerId,
                               @RequestParam String content) {
        if (content == null || content.isBlank()) return "redirect:/questions/" + id;
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String stripped = content.strip();
        answer.setContent(stripped);
        answer.setContentHash(ContentHash.sha256(stripped));
        return "redirect:/questions/" + id;
    }

    // --- presentation helpers ---

    private List<String> buildSynthesisBullets(List<QuestionView> results) {
        Map<String, List<String>> byTopic = new LinkedHashMap<>();
        for (QuestionView q : results) {
            String primaryTopic = q.topics().isEmpty() ? "Uncategorized" : q.topics().get(0);
            byTopic.computeIfAbsent(primaryTopic, k -> new ArrayList<>()).addAll(q.tags());
        }
        List<String> bullets = new ArrayList<>();
        bullets.add(results.size() + (results.size() == 1 ? " question indexed" : " questions indexed")
                + (byTopic.size() > 1 ? ", split across " + byTopic.size() + " topics." : "."));
        byTopic.forEach((t, tags) -> bullets.add(t + ": " + String.join(", ", tags) + "."));
        return bullets;
    }

    private List<TopicGroup> groupByTopic(List<QuestionView> results) {
        Map<String, List<QuestionView>> byTopic = new LinkedHashMap<>();
        for (QuestionView q : results) {
            String primaryTopic = q.topics().isEmpty() ? "Uncategorized" : q.topics().get(0);
            byTopic.computeIfAbsent(primaryTopic, k -> new ArrayList<>()).add(q);
        }
        return byTopic.entrySet().stream()
                .map(e -> new TopicGroup(e.getKey(), e.getValue().size(), e.getValue()))
                .collect(Collectors.toList());
    }

    public record TopicGroup(String topic, int count, List<QuestionView> items) {}
    public record AnswerDetail(UUID id, String source, String rawContent, String htmlContent) {}
}
