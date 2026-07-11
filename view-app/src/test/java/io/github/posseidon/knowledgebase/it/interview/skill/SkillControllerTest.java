package io.github.posseidon.knowledgebase.it.interview.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

class SkillControllerTest {

  private SkillRepository skillRepository;
  private SkillController controller;

  @BeforeEach
  void setUp() {
    skillRepository = mock(SkillRepository.class);
    controller = new SkillController(skillRepository);
  }

  @Test
  void homeReturnsHomeView() {
    assertThat(controller.home(new ExtendedModelMap())).isEqualTo("skill/skills-home");
  }

  @Test
  void groupThrowsNotFoundWhenSkillMissing() {
    UUID id = UUID.randomUUID();
    when(skillRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.group(id, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void groupBuildsBreadcrumbFromRootToCurrent() {
    Skill root = new Skill("Backend", "Backend", null, null, null);
    root.setId(UUID.randomUUID());
    Skill child = new Skill("Java", "Backend -> Java", null, null, root);
    child.setId(UUID.randomUUID());
    when(skillRepository.findById(child.getId())).thenReturn(Optional.of(child));

    Model model = new ExtendedModelMap();
    String view = controller.group(child.getId(), model);

    assertThat(view).isEqualTo("skill/skill-group");
    @SuppressWarnings("unchecked")
    List<SkillController.BreadcrumbItem> breadcrumb =
        (List<SkillController.BreadcrumbItem>) model.getAttribute("breadcrumb");
    assertThat(breadcrumb).extracting(SkillController.BreadcrumbItem::name)
        .containsExactly("Backend", "Java");
  }

  @Test
  void searchReturnsEmptyListForBlankQuery() {
    assertThat(controller.search("  ")).isEmpty();
    assertThat(controller.search(null)).isEmpty();
  }

  @Test
  void searchMapsAndTruncatesLongDescriptions() {
    String longDescription = "x".repeat(250);
    Skill skill = new Skill("Java", "Java", longDescription, 5, null);
    skill.setId(UUID.randomUUID());
    when(skillRepository.search(anyString(), anyInt())).thenReturn(List.of(skill));

    List<SkillController.SkillSearchResult> results = controller.search("java");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).description()).hasSize(201).endsWith("…");
  }

  @Test
  void searchKeepsShortDescriptionsUnchanged() {
    Skill skill = new Skill("Java", "Java", "short desc", 5, null);
    skill.setId(UUID.randomUUID());
    when(skillRepository.search(anyString(), anyInt())).thenReturn(List.of(skill));

    List<SkillController.SkillSearchResult> results = controller.search("java");

    assertThat(results.get(0).description()).isEqualTo("short desc");
  }
}
