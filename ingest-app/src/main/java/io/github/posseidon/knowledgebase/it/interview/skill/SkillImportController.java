package io.github.posseidon.knowledgebase.it.interview.skill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class SkillImportController {

    private final SkillIngestService skillIngestService;

    public SkillImportController(SkillIngestService skillIngestService) {
        this.skillIngestService = skillIngestService;
    }

    @PostMapping("/skills/import")
    public ResponseEntity<Void> importSkills(@RequestParam("file") MultipartFile file) throws IOException {
        // Copy the bytes now: the multipart file's backing storage is tied to this
        // request and may be gone by the time the background import reads it.
        skillIngestService.importFromXlsxAsync(file.getBytes());
        return ResponseEntity.accepted().build();
    }
}
