package io.github.posseidon.knowledgebase.it.interview.ask;

import io.github.posseidon.knowledgebase.it.interview.dto.ask.AskResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

  private final AskService askService;

  public AskController(AskService askService) {
    this.askService = askService;
  }

  @PostMapping("/ask")
  public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
    AskResponse response = askService.ask(request.query());
    return ResponseEntity.ok(response);
  }

  public record AskRequest(String query) {

  }
}
