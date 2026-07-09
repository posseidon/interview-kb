package io.github.posseidon.knowledgebase.it.interview.merge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class MergeController {

    private final MergeService mergeService;

    public MergeController(MergeService mergeService) {
        this.mergeService = mergeService;
    }

    @GetMapping("/merge/candidates")
    public ResponseEntity<List<MergeCandidateDto>> getCandidates(
            @RequestParam(defaultValue = "0.7") float threshold) {
        List<MergeService.MergeCandidate> candidates = mergeService.findCandidates(threshold);
        List<MergeCandidateDto> dtos = candidates.stream()
                .map(c -> new MergeCandidateDto(c.sourceId(), c.targetId(), c.similarity()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/merge")
    public ResponseEntity<Void> merge(@RequestBody MergeRequest request) {
        mergeService.merge(request.targetId(), request.sourceId());
        return ResponseEntity.noContent().build();
    }

    public record MergeCandidateDto(UUID sourceId, UUID targetId, float similarity) {}

    public record MergeRequest(UUID targetId, UUID sourceId) {}
}
