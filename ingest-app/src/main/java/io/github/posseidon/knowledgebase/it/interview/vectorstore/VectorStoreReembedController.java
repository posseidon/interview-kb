package io.github.posseidon.knowledgebase.it.interview.vectorstore;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin trigger for re-embedding every question into {@code vector_store} — a one-off maintenance
 * action (e.g. after switching embedding models), not part of normal ingestion. No auth per this
 * app's single-user model; call it manually (curl/Postman), not from the ingestion pipeline.
 */
@RestController
@RequestMapping("/admin/vector-store")
public class VectorStoreReembedController {

  private final VectorStoreReembedService reembedService;
  private final VectorStoreReembedProgress progress;

  public VectorStoreReembedController(VectorStoreReembedService reembedService,
      VectorStoreReembedProgress progress) {
    this.reembedService = reembedService;
    this.progress = progress;
  }

  @PostMapping("/reembed")
  public ResponseEntity<Void> reembed() {
    boolean started = reembedService.reembedAllAsync();
    return started ? ResponseEntity.accepted().build()
        : ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  @GetMapping("/reembed/status")
  public VectorStoreReembedStatusView status() {
    return new VectorStoreReembedStatusView(progress.isRunning(), progress.total(),
        progress.processed(), progress.error());
  }
}
