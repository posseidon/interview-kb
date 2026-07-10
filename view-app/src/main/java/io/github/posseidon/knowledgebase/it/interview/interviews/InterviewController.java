package io.github.posseidon.knowledgebase.it.interview.interviews;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class InterviewController {

  private final InterviewService interviewService;

  public InterviewController(InterviewService interviewService) {
    this.interviewService = interviewService;
  }

  @GetMapping("/interviews")
  public String list(Model model) {
    model.addAttribute("interviews", interviewService.findAll());
    return "interviews/interviews";
  }

  @GetMapping("/interviews/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    var view = interviewService.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    model.addAttribute("interview", view);
    return "interviews/interview-detail";
  }
}
