package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class SkillImportControllerTest {

  @Test
  void copiesBytesAndTriggersAsyncImportReturning202() throws IOException {
    SkillIngestService service = mock(SkillIngestService.class);
    SkillImportController controller = new SkillImportController(service);
    byte[] content = "workbook-bytes".getBytes();
    MockMultipartFile file = new MockMultipartFile("file", "skills.xlsx",
        "application/octet-stream", content);

    ResponseEntity<Void> response = controller.importSkills(file);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(service).importFromXlsxAsync(content);
  }
}
