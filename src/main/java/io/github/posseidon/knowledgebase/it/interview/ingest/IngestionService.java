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
        int topicsCreated = 0;
        int topicsUpdated = 0;
        int tagsCreated = 0;
        int tagsUpdated = 0;
        int questionsCreated = 0;
        int questionsUpdated = 0;
        int answersAdded = 0;

        // Upsert topics
        Map<String, Topic> topicMap = new HashMap<>();
        for (TopicDto dto : request.topics()) {
            Optional<Topic> existing = topicRepository.findBySlug(dto.slug());
            Topic topic;
            if (existing.isPresent()) {
                topic = existing.get();
                topic.setName(dto.name());
                topic.setDescription(dto.description());
                topicsUpdated++;
            } else {
                topic = new Topic(dto.slug(), dto.name(), dto.description());
                topicsCreated++;
            }
            topic = topicRepository.save(topic);
            topicMap.put(dto.slug(), topic);
        }

        // Upsert tags
        Map<String, Tag> tagMap = new HashMap<>();
        for (QuestionDto qDto : request.questions()) {
            for (String tagName : qDto.tags()) {
                if (!tagMap.containsKey(tagName)) {
                    Optional<Tag> existing = tagRepository.findByName(tagName);
                    Tag tag;
                    if (existing.isPresent()) {
                        tag = existing.get();
                        tagsUpdated++;
                    } else {
                        tag = new Tag(tagName);
                        tagsCreated++;
                    }
                    tag = tagRepository.save(tag);
                    tagMap.put(tagName, tag);
                }
            }
        }

        // Upsert questions with answers and mirror to vector store
        for (QuestionDto qDto : request.questions()) {
            String contentHash = ContentHash.sha256(qDto.content());
            Question question;
            boolean isNew = false;

            Optional<Question> byExternalId = qDto.externalId() != null ?
                    questionRepository.findByExternalId(qDto.externalId()) : Optional.empty();

            if (byExternalId.isPresent()) {
                question = byExternalId.get();
                if (!question.getContentHash().equals(contentHash)) {
                    question.setContent(qDto.content());
                    question.setContentHash(contentHash);
                    vectorStore.delete(List.of(question.getId().toString()));
                }
                questionsUpdated++;
            } else {
                Optional<Question> byContentHash = questionRepository.findByContentHash(contentHash);
                if (byContentHash.isPresent()) {
                    question = byContentHash.get();
                    questionsUpdated++;
                } else {
                    question = new Question(qDto.content(), contentHash);
                    isNew = true;
                    questionsCreated++;
                }
            }

            question.setExternalId(qDto.externalId());
            question.setRequiresImpl(qDto.requiresImpl());
            question.setLanguage(qDto.language());

            // Update topics
            Set<Topic> questionTopics = qDto.topics().stream()
                    .map(slug -> topicMap.getOrDefault(slug, null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            question.setTopics(questionTopics);

            // Update tags
            Set<Tag> questionTags = qDto.tags().stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            question.setTags(questionTags);

            question = questionRepository.save(question);

            // Upsert answers
            Set<String> existingHashes = question.getAnswers().stream()
                    .map(Answer::getContentHash)
                    .collect(Collectors.toSet());

            for (AnswerDto aDto : qDto.answers()) {
                String answerHash = ContentHash.sha256(aDto.content());
                if (!existingHashes.contains(answerHash)) {
                    Answer answer = new Answer(question, aDto.content(), answerHash, aDto.source());
                    answerRepository.save(answer);
                    answersAdded++;
                }
            }

            // Mirror to vector store
            List<String> topicSlugs = questionTopics.stream().map(Topic::getSlug).toList();
            List<String> tagNames = questionTags.stream().map(Tag::getName).toList();
            Map<String, Object> metadata = Map.of(
                    "topics", topicSlugs,
                    "tags", tagNames,
                    "frequency", question.getFrequency()
            );
            Document doc = Document.builder()
                    .id(question.getId().toString())
                    .text(question.getContent())
                    .metadata(metadata)
                    .build();
            vectorStore.add(List.of(doc));
        }

        return new IngestResponse(topicsCreated, topicsUpdated, tagsCreated, tagsUpdated,
                questionsCreated, questionsUpdated, answersAdded);
    }
}
