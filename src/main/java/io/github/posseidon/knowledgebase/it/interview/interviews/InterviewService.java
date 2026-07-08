package io.github.posseidon.knowledgebase.it.interview.interviews;

import io.github.posseidon.knowledgebase.it.interview.domain.*;
import io.github.posseidon.knowledgebase.it.interview.dto.*;
import io.github.posseidon.knowledgebase.it.interview.ingest.ContentHash;
import io.github.posseidon.knowledgebase.it.interview.repo.*;
import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final TagRepository tagRepository;
    private final AnswerRepository answerRepository;
    private final VectorStore vectorStore;
    private final QuestionMapper questionMapper;

    public InterviewService(InterviewRepository interviewRepository,
                            QuestionRepository questionRepository,
                            TopicRepository topicRepository,
                            TagRepository tagRepository,
                            AnswerRepository answerRepository,
                            VectorStore vectorStore,
                            QuestionMapper questionMapper) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.tagRepository = tagRepository;
        this.answerRepository = answerRepository;
        this.vectorStore = vectorStore;
        this.questionMapper = questionMapper;
    }

    @Transactional
    public InterviewIngestResponse ingest(InterviewDto dto) {
        List<Question> inlineQuestions = upsertInlineQuestions(dto.questions());

        List<Question> referenced = dto.questionIds() != null && !dto.questionIds().isEmpty()
                ? questionRepository.findAllByExternalIdIn(dto.questionIds())
                : List.of();

        Map<UUID, Question> allById = new LinkedHashMap<>();
        inlineQuestions.forEach(q -> allById.put(q.getId(), q));
        referenced.forEach(q -> allById.put(q.getId(), q));

        Interview interview = new Interview();
        interview.setProjectCode(dto.projectCode());
        interview.setDate(dto.date());
        interview.setFeedback(dto.feedback());
        interview.setUpskillingPlan(dto.upskillingPlan());
        interview.setDecision(dto.decision());
        interview.setQuestions(new HashSet<>(allById.values()));

        interview = interviewRepository.save(interview);
        return new InterviewIngestResponse(
                interview.getId(), interview.getProjectCode(), interview.getQuestions().size());
    }

    @Transactional
    public InterviewIngestResponse addQuestions(InterviewDto dto) {
        Interview interview = interviewRepository.findByProjectCode(dto.projectCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Interview not found for project_code: " + dto.projectCode()));

        List<Question> inlineQuestions = upsertInlineQuestions(dto.questions());

        List<Question> referenced = dto.questionIds() != null && !dto.questionIds().isEmpty()
                ? questionRepository.findAllByExternalIdIn(dto.questionIds())
                : List.of();

        Map<UUID, Question> allById = new LinkedHashMap<>();
        inlineQuestions.forEach(q -> allById.put(q.getId(), q));
        referenced.forEach(q -> allById.put(q.getId(), q));

        interview.getQuestions().addAll(allById.values());
        interviewRepository.save(interview);

        return new InterviewIngestResponse(
                interview.getId(), interview.getProjectCode(), interview.getQuestions().size());
    }

    @Transactional(readOnly = true)
    public List<InterviewView> findAll() {
        return interviewRepository.findAllByOrderByDateAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<InterviewView> findById(UUID id) {
        return interviewRepository.findById(id).map(this::toView);
    }

    // --- inline question upsert (batch-optimized: constant number of DB round-trips) ---

    private List<Question> upsertInlineQuestions(List<QuestionDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return List.of();

        // Phase 1: collect all unique keys across all DTOs
        Set<String> allSlugs = new HashSet<>();
        Set<String> allTagNames = new HashSet<>();
        Set<String> allExternalIds = new HashSet<>();
        for (QuestionDto qDto : dtos) {
            allSlugs.addAll(qDto.topics());
            allTagNames.addAll(qDto.tags());
            if (qDto.externalId() != null) allExternalIds.add(qDto.externalId());
        }

        // Phase 2: batch fetch topics and tags — 2 queries
        Map<String, Topic> topicMap = topicRepository.findAllBySlugIn(allSlugs)
                .stream().collect(Collectors.toMap(Topic::getSlug, t -> t));
        Map<String, Tag> tagMap = tagRepository.findAllByNameIn(allTagNames)
                .stream().collect(Collectors.toMap(Tag::getName, t -> t));

        // Phase 3: create missing topics and tags — at most 2 queries
        List<Topic> newTopics = allSlugs.stream().filter(s -> !topicMap.containsKey(s))
                .map(s -> new Topic(s, s, null)).toList();
        topicRepository.saveAll(newTopics).forEach(t -> topicMap.put(t.getSlug(), t));

        List<Tag> newTags = allTagNames.stream().filter(n -> !tagMap.containsKey(n))
                .map(Tag::new).toList();
        tagRepository.saveAll(newTags).forEach(t -> tagMap.put(t.getName(), t));

        // Phase 4: batch fetch existing questions — 2 queries
        Map<String, Question> byExternalId = questionRepository.findAllByExternalIdIn(allExternalIds)
                .stream().collect(Collectors.toMap(Question::getExternalId, q -> q));

        Set<String> allContentHashes = dtos.stream()
                .map(d -> ContentHash.sha256(d.content())).collect(Collectors.toSet());
        Map<String, Question> byContentHash = questionRepository.findAllByContentHashIn(allContentHashes)
                .stream().collect(Collectors.toMap(Question::getContentHash, q -> q));

        // Phase 5: resolve each question entity in memory
        List<String> vectorIdsToDelete = new ArrayList<>();
        Set<UUID> preExistingIds = new HashSet<>();
        List<Question> toSave = new ArrayList<>(dtos.size());

        for (QuestionDto qDto : dtos) {
            String contentHash = ContentHash.sha256(qDto.content());
            Question question;
            if (qDto.externalId() != null && byExternalId.containsKey(qDto.externalId())) {
                question = byExternalId.get(qDto.externalId());
                if (!question.getContentHash().equals(contentHash)) {
                    question.setContent(qDto.content());
                    question.setContentHash(contentHash);
                    vectorIdsToDelete.add(question.getId().toString());
                }
                preExistingIds.add(question.getId());
            } else if (byContentHash.containsKey(contentHash)) {
                question = byContentHash.get(contentHash);
                preExistingIds.add(question.getId());
            } else {
                question = new Question(qDto.content(), contentHash);
            }
            question.setExternalId(qDto.externalId());
            question.setRequiresImpl(qDto.requiresImpl());
            question.setLanguage(qDto.language());
            question.setTopics(qDto.topics().stream().map(topicMap::get)
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
            question.setTags(qDto.tags().stream().map(tagMap::get)
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
            toSave.add(question);
        }

        // Phase 6: batch fetch existing answer hashes — 1 query
        Map<UUID, Set<String>> existingAnswerHashes = preExistingIds.isEmpty()
                ? Map.of()
                : answerRepository.findByQuestionIds(preExistingIds).stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getQuestion().getId(),
                                Collectors.mapping(Answer::getContentHash, Collectors.toSet())));

        // Phase 7: delete stale vectors, batch save questions — 1 saveAll
        if (!vectorIdsToDelete.isEmpty()) vectorStore.delete(vectorIdsToDelete);
        List<Question> saved = questionRepository.saveAll(toSave);

        // Phase 8: batch save answers — 1 saveAll
        List<Answer> answersToSave = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            QuestionDto qDto = dtos.get(i);
            Question q = saved.get(i);
            Set<String> existingHashes = existingAnswerHashes.getOrDefault(q.getId(), Set.of());
            for (AnswerDto aDto : qDto.answers()) {
                if (aDto.content() == null || aDto.content().isBlank()) continue;
                String answerHash = ContentHash.sha256(aDto.content());
                if (!existingHashes.contains(answerHash)) {
                    answersToSave.add(new Answer(q, aDto.content(), answerHash,
                            aDto.source() != null ? aDto.source() : "human"));
                }
            }
        }
        if (!answersToSave.isEmpty()) answerRepository.saveAll(answersToSave);

        // Phase 9: single vectorStore.add — 1 Ollama HTTP request for all embeddings
        List<Document> docs = saved.stream()
                .map(q -> Document.builder()
                        .id(q.getId().toString()).text(q.getContent())
                        .metadata(Map.of(
                                "topics", q.getTopics().stream().map(Topic::getSlug).toList(),
                                "tags", q.getTags().stream().map(Tag::getName).toList(),
                                "frequency", q.getFrequency()))
                        .build())
                .toList();
        vectorStore.add(docs);

        return saved;
    }

    // --- view mapping ---

    private InterviewView toView(Interview iv) {
        List<QuestionView> questionViews = iv.getQuestions().stream()
                .sorted(Comparator.comparing(Question::getContent))
                .map(questionMapper::toView)
                .toList();

        Map<String, List<QuestionView>> grouped = new TreeMap<>();
        for (QuestionView qv : questionViews) {
            List<String> topics = qv.topics().isEmpty() ? List.of("General") : qv.topics();
            for (String topic : topics) {
                grouped.computeIfAbsent(topic, k -> new ArrayList<>()).add(qv);
            }
        }
        List<TopicGroup> questionsByTopic = grouped.entrySet().stream()
                .map(e -> new TopicGroup(e.getKey(), e.getValue()))
                .toList();

        return new InterviewView(
                iv.getId(),
                iv.getProjectCode(),
                iv.getDate().format(DATE_FMT),
                iv.getDecision(),
                decisionCssClass(iv.getDecision()),
                decisionLabel(iv.getDecision()),
                Markdown.toHtml(iv.getFeedback()),
                Markdown.toHtml(iv.getUpskillingPlan()),
                Markdown.toSnippet(iv.getFeedback(), 160),
                iv.getQuestions().size(),
                questionsByTopic
        );
    }

    private String decisionCssClass(Decision d) {
        return switch (d) {
            case NO_HIRE -> "no-hire";
            case MAYBE -> "maybe";
            case GOOD_CANDIDATE -> "good";
        };
    }

    private String decisionLabel(Decision d) {
        return switch (d) {
            case NO_HIRE -> "NO HIRE";
            case MAYBE -> "MAYBE";
            case GOOD_CANDIDATE -> "GOOD CANDIDATE";
        };
    }
}
