package io.github.posseidon.knowledgebase.it.interview.ingest;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.AnswerDto;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.IngestRequest;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.response.IngestResponse;
import io.github.posseidon.knowledgebase.it.interview.repo.*;
import io.github.posseidon.knowledgebase.it.interview.skill.SkillResolver;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IngestionService {

    private final SkillResolver skillResolver;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final VectorStore vectorStore;

    public IngestionService(SkillResolver skillResolver,
                            QuestionRepository questionRepository, AnswerRepository answerRepository,
                            VectorStore vectorStore) {
        this.skillResolver = skillResolver;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public IngestResponse ingest(IngestRequest request) {
        Set<String> allSkillNames = request.questions().stream()
                .flatMap(q -> q.skills().stream()).collect(Collectors.toSet());
        Map<String, Skill> skillByName = skillResolver.resolve(allSkillNames);

        // Phase 1: batch fetch existing questions by externalId and contentHash — 2 queries
        Set<String> allExternalIds = request.questions().stream()
                .map(QuestionDto::externalId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Question> byExternalId = questionRepository.findAllByExternalIdIn(allExternalIds)
                .stream().collect(Collectors.toMap(Question::getExternalId, q -> q));

        Set<String> allContentHashes = request.questions().stream()
                .map(d -> ContentHash.sha256(d.content())).collect(Collectors.toSet());
        Map<String, Question> byContentHash = questionRepository.findAllByContentHashIn(allContentHashes)
                .stream().collect(Collectors.toMap(Question::getContentHash, q -> q));

        // Phase 2: resolve each question entity in memory
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
            question.setSkills(qDto.skills().stream().map(skillByName::get)
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
            toSave.add(question);
        }

        // Phase 3: batch fetch existing answer hashes — 1 query
        Map<UUID, Set<String>> existingAnswerHashes = preExistingIds.isEmpty()
                ? Map.of()
                : answerRepository.findByQuestionIds(preExistingIds).stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getQuestion().getId(),
                                Collectors.mapping(Answer::getContentHash, Collectors.toSet())));

        // Phase 4: delete stale vectors, batch save questions — 1 saveAll
        if (!vectorIdsToDelete.isEmpty()) vectorStore.delete(vectorIdsToDelete);
        List<Question> saved = questionRepository.saveAll(toSave);

        // Phase 5: batch save new answers — 1 saveAll
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

        // Phase 6: single vectorStore.add — 1 Ollama HTTP request for all embeddings
        List<Document> docs = saved.stream()
                .map(q -> Document.builder()
                        .id(q.getId().toString()).text(q.getContent())
                        .metadata(Map.of(
                                "skills", q.getSkills().stream().map(Skill::getName).toList(),
                                "frequency", q.getFrequency()))
                        .build())
                .toList();
        vectorStore.add(docs);

        return new IngestResponse(questionsCreated, questionsUpdated, answersAdded);
    }
}
