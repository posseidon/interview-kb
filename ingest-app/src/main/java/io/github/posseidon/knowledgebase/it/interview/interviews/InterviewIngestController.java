package io.github.posseidon.knowledgebase.it.interview.interviews;

import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewDto;
import io.github.posseidon.knowledgebase.it.interview.dto.interview.InterviewIngestResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewIngestController {

  private final InterviewIngestService interviewIngestService;

  public InterviewIngestController(InterviewIngestService interviewIngestService) {
    this.interviewIngestService = interviewIngestService;
  }

  @PostMapping("/interviews")
  public InterviewIngestResponse ingest(@RequestBody InterviewDto dto) {
    return interviewIngestService.ingest(dto);
  }

  @PostMapping("/interviews/questions")
  public InterviewIngestResponse addQuestions(@RequestBody InterviewDto dto) {
    return interviewIngestService.addQuestions(dto);
  }
}
