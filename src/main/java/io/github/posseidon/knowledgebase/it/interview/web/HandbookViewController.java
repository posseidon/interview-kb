package io.github.posseidon.knowledgebase.it.interview.web;

import io.github.posseidon.knowledgebase.it.interview.ask.AskService;
import io.github.posseidon.knowledgebase.it.interview.domain.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.Tag;
import io.github.posseidon.knowledgebase.it.interview.domain.Topic;
import io.github.posseidon.knowledgebase.it.interview.dto.AnswerView;
import io.github.posseidon.knowledgebase.it.interview.dto.AskResponse;
import io.github.posseidon.knowledgebase.it.interview.dto.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.ingest.ContentHash;
import io.github.posseidon.knowledgebase.it.interview.repo.AnswerRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.TopicRepository;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
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

    private static final Parser MD_PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private static final HtmlRenderer MD_RENDERER = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AskService askService;
    private final VectorStore vectorStore;

    public HandbookViewController(TopicRepository topicRepository,
                                  QuestionRepository questionRepository,
                                  AnswerRepository answerRepository,
                                  AskService askService,
                                  VectorStore vectorStore) {
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.askService = askService;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<TopicWithCount> topics = topicRepository.findAll().stream()
                .map(t -> new TopicWithCount(t.getSlug(), t.getName(),
                        (int) questionRepository.countByTopicSlug(t.getSlug())))
                .sorted(Comparator.comparing(TopicWithCount::name))
                .collect(Collectors.toList());

        model.addAttribute("topics", topics);
        model.addAttribute("totalQuestions", topics.stream().mapToInt(TopicWithCount::count).sum());
        model.addAttribute("totalTopics", topics.size());
        return "home";
    }

    @Transactional(readOnly = true)
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String topic,
                         Model model) {
        boolean isAsk = q != null && !q.isBlank();
        List<QuestionView> results;
        String displayQuery;

        if (isAsk) {
            AskResponse resp = askService.ask(q);
            results = resp.sources();
            displayQuery = q;
            model.addAttribute("synthesisBullets", buildSynthesisBullets(results));
        } else {
            List<Question> entities = questionRepository.findByTopicSlug(
                    topic, PageRequest.of(0, 50));
            results = entities.stream().map(this::toQuestionView).toList();
            displayQuery = topicRepository.findBySlug(topic)
                    .map(Topic::getName)
                    .orElse(topic);
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
                .map(a -> new AnswerDetail(a.getId(), a.getSource(), a.getContent(), renderMarkdown(a.getContent())))
                .toList();

        model.addAttribute("question", toQuestionView(question));
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
            List<String> topicSlugs = question.getTopics().stream().map(Topic::getSlug).toList();
            List<String> tagNames = question.getTags().stream().map(Tag::getName).toList();
            Document doc = Document.builder()
                    .id(id.toString())
                    .text(stripped)
                    .metadata(Map.of("topics", topicSlugs, "tags", tagNames, "frequency", question.getFrequency()))
                    .build();
            vectorStore.add(List.of(doc));
        }
        return "redirect:/questions/" + id;
    }

    @Transactional
    @PostMapping("/questions/{id}/answers")
    public String addAnswer(@PathVariable UUID id,
                            @RequestParam String content) {
        if (content == null || content.isBlank()) return "redirect:/questions/" + id;
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        answerRepository.save(new Answer(question, content.strip(),
                ContentHash.sha256(content.strip()), "human"));
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
        answer.setContent(content.strip());
        answer.setContentHash(ContentHash.sha256(content.strip()));
        return "redirect:/questions/" + id;
    }

    // --- presentation helpers ---

    private String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        Node document = MD_PARSER.parse(markdown);
        return MD_RENDERER.render(document);
    }

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

    private QuestionView toQuestionView(Question q) {
        List<String> topics = q.getTopics().stream().map(Topic::getSlug).toList();
        List<String> tags = q.getTags().stream().map(Tag::getName).toList();
        List<AnswerView> answers = q.getAnswers().stream()
                .map(a -> new AnswerView(a.getId(), a.getSource(), a.getContent()))
                .toList();
        return new QuestionView(q.getId(), q.getExternalId(), q.getContent(),
                q.getRequiresImpl(), q.getLanguage(), q.getFrequency(), topics, tags, answers);
    }

    public record TopicWithCount(String slug, String name, int count) {}
    public record TopicGroup(String topic, int count, List<QuestionView> items) {}
    public record AnswerDetail(UUID id, String source, String rawContent, String htmlContent) {}
}
