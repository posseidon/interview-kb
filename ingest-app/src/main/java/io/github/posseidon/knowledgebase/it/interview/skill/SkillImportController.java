package io.github.posseidon.knowledgebase.it.interview.skill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SkillImportController {

    private final SkillIngestService skillIngestService;

    public SkillImportController(SkillIngestService skillIngestService) {
        this.skillIngestService = skillIngestService;
    }

    @PostMapping("/skills/import")
    public ResponseEntity<SkillImportResponse> importSkills(@RequestParam("file") MultipartFile file) {
        int imported = skillIngestService.importFromXlsx(file);
        return ResponseEntity.ok(new SkillImportResponse(imported));
    }

    public record SkillImportResponse(int imported) {}
}
