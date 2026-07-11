package io.github.posseidon.knowledgebase.it.interview.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;
import io.github.posseidon.knowledgebase.it.interview.domain.interview.Interview;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewIngestResponse;
import io.github.posseidon.knowledgebase.it.interview.ingest.QuestionUpsertService;
import io.github.posseidon.knowledgebase.it.interview.repo.InterviewRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewIngestServiceTest {

  private InterviewRepository interviewRepository;
  private QuestionRepository questionRepository;
  private QuestionUpsertService questionUpsertService;
  private InterviewIngestService service;

  @BeforeEach
  void setUp() {
    interviewRepository = mock(InterviewRepository.class);
    questionRepository = mock(QuestionRepository.class);
    questionUpsertService = mock(QuestionUpsertService.class);
    service = new InterviewIngestService(interviewRepository, questionRepository,
        questionUpsertService);

    when(interviewRepository.save(any())).thenAnswer(inv -> {
      Interview iv = inv.getArgument(0);
      iv.setId(UUID.randomUUID());
      return iv;
    });
  }

  private static Question question() {
    Question q = new Question("content", "hash");
    q.setId(UUID.randomUUID());
    return q;
  }

  @Test
  void ingestCombinesInlineAndReferencedQuestions() {
    Question inline = question();
    Question referenced = question();
    when(questionUpsertService.upsert(any()))
        .thenReturn(new QuestionUpsertService.Result(List.of(inline), 1, 0, 0));
    when(questionRepository.findAllByExternalIdIn(anyCollection()))
        .thenReturn(List.of(referenced));

    InterviewDto dto = new InterviewDto("PROJ-1", LocalDate.now(), "feedback", "plan",
        Decision.GOOD_CANDIDATE, List.of("ext-2"), List.of());

    InterviewIngestResponse response = service.ingest(dto);

    assertThat(response.projectCode()).isEqualTo("PROJ-1");
    assertThat(response.questionsLinked()).isEqualTo(2);
  }

  @Test
  void ingestSkipsReferencedLookupWhenNoQuestionIds() {
    when(questionUpsertService.upsert(any()))
        .thenReturn(new QuestionUpsertService.Result(List.of(), 0, 0, 0));

    InterviewDto dto = new InterviewDto("PROJ-1", LocalDate.now(), "feedback", "plan",
        Decision.MAYBE, List.of(), List.of());

    service.ingest(dto);

    org.mockito.Mockito.verify(questionRepository, org.mockito.Mockito.never())
        .findAllByExternalIdIn(anyCollection());
  }

  @Test
  void addQuestionsAppendsToExistingInterview() {
    Interview interview = new Interview();
    interview.setId(UUID.randomUUID());
    interview.setProjectCode("PROJ-1");
    when(interviewRepository.findByProjectCode("PROJ-1")).thenReturn(Optional.of(interview));
    Question newQuestion = question();
    when(questionUpsertService.upsert(any()))
        .thenReturn(new QuestionUpsertService.Result(List.of(newQuestion), 1, 0, 0));

    InterviewDto dto = new InterviewDto("PROJ-1", LocalDate.now(), "feedback", "plan",
        Decision.NO_HIRE, List.of(), List.of());

    InterviewIngestResponse response = service.addQuestions(dto);

    assertThat(interview.getQuestions()).contains(newQuestion);
    assertThat(response.questionsLinked()).isEqualTo(1);
  }

  @Test
  void addQuestionsThrowsWhenInterviewNotFound() {
    when(interviewRepository.findByProjectCode("MISSING")).thenReturn(Optional.empty());

    InterviewDto dto = new InterviewDto("MISSING", LocalDate.now(), null, null,
        Decision.MAYBE, List.of(), List.of());

    assertThatThrownBy(() -> service.addQuestions(dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MISSING");
  }
}
