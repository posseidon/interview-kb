package io.github.posseidon.knowledgebase.it.interview.interviews;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Interview;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.AnswerDto;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewIngestResponse;
import io.github.posseidon.knowledgebase.it.interview.skill.SkillResolver;
import io.github.posseidon.knowledgebase.it.interview.repo.*;
import io.github.posseidon.knowledgebase.it.interview.util.ContentHash;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InterviewIngestService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final SkillResolver skillResolver;
    private final AnswerRepository answerRepository;
    private final VectorStore vectorStore;

    public InterviewIngestService(InterviewRepository interviewRepository,
                                  QuestionRepository questionRepository,
                                  SkillResolver skillResolver,
                                  AnswerRepository answerRepository,
                                  VectorStore vectorStore) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.skillResolver = skillResolver;
        this.answerRepository = answerRepository;
        this.vectorStore = vectorStore;
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

    private List<Question> upsertInlineQuestions(List<QuestionDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return List.of();

        Set<String> allSkillNames = new HashSet<>();
        Set<String> allExternalIds = new HashSet<>();
        for (QuestionDto qDto : dtos) {
            allSkillNames.addAll(qDto.skills());
            if (qDto.externalId() != null) allExternalIds.add(qDto.externalId());
        }

        Map<String, Skill> skillByName = skillResolver.resolve(allSkillNames);

        Map<String, Question> byExternalId = questionRepository.findAllByExternalIdIn(allExternalIds)
                .stream().collect(Collectors.toMap(Question::getExternalId, q -> q));

        Set<String> allContentHashes = dtos.stream()
                .map(d -> ContentHash.sha256(d.content())).collect(Collectors.toSet());
        Map<String, Question> byContentHash = questionRepository.findAllByContentHashIn(allContentHashes)
                .stream().collect(Collectors.toMap(Question::getContentHash, q -> q));

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
            question.setSkills(qDto.skills().stream().map(skillByName::get)
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
            toSave.add(question);
        }

        Map<UUID, Set<String>> existingAnswerHashes = preExistingIds.isEmpty()
                ? Map.of()
                : answerRepository.findByQuestionIds(preExistingIds).stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getQuestion().getId(),
                                Collectors.mapping(Answer::getContentHash, Collectors.toSet())));

        if (!vectorIdsToDelete.isEmpty()) vectorStore.delete(vectorIdsToDelete);
        List<Question> saved = questionRepository.saveAll(toSave);

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

        List<Document> docs = saved.stream()
                .map(q -> Document.builder()
                        .id(q.getId().toString()).text(q.getContent())
                        .metadata(Map.of(
                                "skills", q.getSkills().stream().map(Skill::getName).toList(),
                                "frequency", q.getFrequency()))
                        .build())
                .toList();
        vectorStore.add(docs);

        return saved;
    }
}
