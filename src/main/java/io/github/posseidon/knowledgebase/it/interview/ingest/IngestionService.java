package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.domain.*;
import io.github.posseidon.knowledgebase.it.interview.dto.*;
import io.github.posseidon.knowledgebase.it.interview.repo.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IngestionService {

    private final TopicRepository topicRepository;
    private final TagRepository tagRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final VectorStore vectorStore;

    public IngestionService(TopicRepository topicRepository, TagRepository tagRepository,
                            QuestionRepository questionRepository, AnswerRepository answerRepository,
                            VectorStore vectorStore) {
        this.topicRepository = topicRepository;
        this.tagRepository = tagRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public IngestResponse ingest(IngestRequest request) {
        // Phase 1: batch fetch existing topics, update name/description, create missing — 1 fetch + 1 saveAll
        Set<String> requestedSlugs = request.topics().stream()
                .map(TopicDto::slug).collect(Collectors.toSet());
        Map<String, Topic> topicMap = topicRepository.findAllBySlugIn(requestedSlugs)
                .stream().collect(Collectors.toMap(Topic::getSlug, t -> t));

        int topicsCreated = 0, topicsUpdated = 0;
        List<Topic> topicsToSave = new ArrayList<>(request.topics().size());
        for (TopicDto dto : request.topics()) {
            Topic topic = topicMap.get(dto.slug());
            if (topic != null) {
                topic.setName(dto.name());
                topic.setDescription(dto.description());
                topicsUpdated++;
            } else {
                topic = new Topic(dto.slug(), dto.name(), dto.description());
                topicsCreated++;
            }
            topicsToSave.add(topic);
        }
        topicRepository.saveAll(topicsToSave).forEach(t -> topicMap.put(t.getSlug(), t));

        // Phase 2: batch fetch existing tags, create missing — 1 fetch + 1 saveAll
        Set<String> allTagNames = request.questions().stream()
                .flatMap(q -> q.tags().stream()).collect(Collectors.toSet());
        Map<String, Tag> tagMap = tagRepository.findAllByNameIn(allTagNames)
                .stream().collect(Collectors.toMap(Tag::getName, t -> t));

        int tagsCreated = 0;
        List<Tag> newTags = allTagNames.stream().filter(n -> !tagMap.containsKey(n))
                .map(Tag::new).toList();
        if (!newTags.isEmpty()) {
            tagRepository.saveAll(newTags).forEach(t -> tagMap.put(t.getName(), t));
            tagsCreated = newTags.size();
        }
        int tagsUpdated = allTagNames.size() - tagsCreated;

        // Phase 3: batch fetch existing questions by externalId and contentHash — 2 queries
        Set<String> allExternalIds = request.questions().stream()
                .map(QuestionDto::externalId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Question> byExternalId = questionRepository.findAllByExternalIdIn(allExternalIds)
                .stream().collect(Collectors.toMap(Question::getExternalId, q -> q));

        Set<String> allContentHashes = request.questions().stream()
                .map(d -> ContentHash.sha256(d.content())).collect(Collectors.toSet());
        Map<String, Question> byContentHash = questionRepository.findAllByContentHashIn(allContentHashes)
                .stream().collect(Collectors.toMap(Question::getContentHash, q -> q));

        // Phase 4: resolve each question entity in memory
        int questionsCreated = 0, questionsUpdated = 0;
        List<String> vectorIdsToDelete = new ArrayList<>();
        Set<UUID> preExistingIds = new HashSet<>();
        List<Question> toSave = new ArrayList<>(request.questions().size());

        for (QuestionDto qDto : request.questions()) {
            String contentHash = ContentHash.sha256(qDto.content());
            Question question;
            if (qDto.externalId() != null && byExternalId.containsKey(qDto.externalId())) {
                question = byExternalId.get(qDto.externalId());
                if (!question.getContentHash().equals(contentHash)) {
                    question.setContent(qDto.content());
                    question.setContentHash(contentHash);
                    vectorIdsToDelete.add(question.getId().toString());
                }
                questionsUpdated++;
                preExistingIds.add(question.getId());
            } else if (byContentHash.containsKey(contentHash)) {
                question = byContentHash.get(contentHash);
                questionsUpdated++;
                preExistingIds.add(question.getId());
            } else {
                question = new Question(qDto.content(), contentHash);
                questionsCreated++;
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

        // Phase 5: batch fetch existing answer hashes — 1 query
        Map<UUID, Set<String>> existingAnswerHashes = preExistingIds.isEmpty()
                ? Map.of()
                : answerRepository.findByQuestionIds(preExistingIds).stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getQuestion().getId(),
                                Collectors.mapping(Answer::getContentHash, Collectors.toSet())));

        // Phase 6: delete stale vectors, batch save questions — 1 saveAll
        if (!vectorIdsToDelete.isEmpty()) vectorStore.delete(vectorIdsToDelete);
        List<Question> saved = questionRepository.saveAll(toSave);

        // Phase 7: batch save new answers — 1 saveAll
        int answersAdded = 0;
        List<Answer> answersToSave = new ArrayList<>();
        for (int i = 0; i < request.questions().size(); i++) {
            QuestionDto qDto = request.questions().get(i);
            Question q = saved.get(i);
            Set<String> existingHashes = existingAnswerHashes.getOrDefault(q.getId(), Set.of());
            for (AnswerDto aDto : qDto.answers()) {
                if (aDto.content() == null || aDto.content().isBlank()) continue;
                String answerHash = ContentHash.sha256(aDto.content());
                if (!existingHashes.contains(answerHash)) {
                    answersToSave.add(new Answer(q, aDto.content(), answerHash,
                            aDto.source() != null ? aDto.source() : "human"));
                    answersAdded++;
                }
            }
        }
        if (!answersToSave.isEmpty()) answerRepository.saveAll(answersToSave);

        // Phase 8: single vectorStore.add — 1 Ollama HTTP request for all embeddings
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

        return new IngestResponse(topicsCreated, topicsUpdated, tagsCreated, tagsUpdated,
                questionsCreated, questionsUpdated, answersAdded);
    }
}
