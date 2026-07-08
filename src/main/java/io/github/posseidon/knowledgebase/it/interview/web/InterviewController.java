package io.github.posseidon.knowledgebase.it.interview.web;

import io.github.posseidon.knowledgebase.it.interview.dto.InterviewDto;
import io.github.posseidon.knowledgebase.it.interview.dto.InterviewIngestResponse;
import io.github.posseidon.knowledgebase.it.interview.interviews.InterviewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Controller
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/interviews")
    public String list(Model model) {
        model.addAttribute("interviews", interviewService.findAll());
        return "interviews";
    }

    @GetMapping("/interviews/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var view = interviewService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("interview", view);
        return "interview-detail";
    }

    @PostMapping("/interviews")
    @ResponseBody
    public InterviewIngestResponse ingest(@RequestBody InterviewDto dto) {
        return interviewService.ingest(dto);
    }

    @PostMapping("/interviews/questions")
    @ResponseBody
    public InterviewIngestResponse addQuestions(@RequestBody InterviewDto dto) {
        return interviewService.addQuestions(dto);
    }
}
