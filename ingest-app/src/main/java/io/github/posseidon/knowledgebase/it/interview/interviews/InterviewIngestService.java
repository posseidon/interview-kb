package io.github.posseidon.knowledgebase.it.interview.interviews;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Interview;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewIngestResponse;
import io.github.posseidon.knowledgebase.it.interview.ingest.QuestionUpsertService;
import io.github.posseidon.knowledgebase.it.interview.repo.InterviewRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class InterviewIngestService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final QuestionUpsertService questionUpsertService;

    public InterviewIngestService(InterviewRepository interviewRepository,
                                  QuestionRepository questionRepository,
                                  QuestionUpsertService questionUpsertService) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.questionUpsertService = questionUpsertService;
    }

    @Transactional
    public InterviewIngestResponse ingest(InterviewDto dto) {
        Map<UUID, Question> allById = resolveQuestions(dto);

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

    private Map<UUID, Question> resolveQuestions(InterviewDto dto) {
        List<Question> inlineQuestions = upsertInlineQuestions(dto.questions());

        List<Question> referenced = dto.questionIds() != null && !dto.questionIds().isEmpty()
                ? questionRepository.findAllByExternalIdIn(dto.questionIds())
                : List.of();

        Map<UUID, Question> allById = new LinkedHashMap<>();
        inlineQuestions.forEach(q -> allById.put(q.getId(), q));
        referenced.forEach(q -> allById.put(q.getId(), q));
        return allById;
    }

    private List<Question> upsertInlineQuestions(List<QuestionDto> dtos) {
        return questionUpsertService.upsert(dtos).questions();
    }

    @Transactional
    public InterviewIngestResponse addQuestions(InterviewDto dto) {
        Interview interview = interviewRepository.findByProjectCode(dto.projectCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Interview not found for project_code: " + dto.projectCode()));

        Map<UUID, Question> allById = resolveQuestions(dto);
        interview.getQuestions().addAll(allById.values());
        interviewRepository.save(interview);

        return new InterviewIngestResponse(
                interview.getId(), interview.getProjectCode(), interview.getQuestions().size());
    }
}
