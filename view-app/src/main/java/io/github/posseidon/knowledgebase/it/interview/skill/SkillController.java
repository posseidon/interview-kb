package io.github.posseidon.knowledgebase.it.interview.skill;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Transactional(readOnly = true)
public class SkillController {

  private static final int MAX_LENGTH = 200;
  private final SkillRepository skillRepository;

  public SkillController(SkillRepository skillRepository) {
    this.skillRepository = skillRepository;
  }

  private static String truncate(String text) {
      if (text == null || text.length() <= MAX_LENGTH) {
          return text;
      }
    return text.substring(0, MAX_LENGTH).strip() + "…";
  }

  @GetMapping("/skills")
  public String home(Model model) {
    return "skill/skills-home";
  }

  @GetMapping("/skills/{id}")
  public String group(@PathVariable UUID id, Model model) {
    Skill current = skillRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // current's own child count is exactly childSkills.size() — no extra query needed.
    model.addAttribute("current", toView(current));
    model.addAttribute("breadcrumb", buildBreadcrumb(current));
    model.addAttribute("levels", SkillLevel.values());
    return "skill/skill-group";
  }

  private SkillNodeView toView(Skill skill) {
    return new SkillNodeView(skill.getId(), skill.getName(), skill.getDescription(),
        skill.getPath(), skill.getPositionCount(), 0,
        skill.getNoviceCriteria(), skill.getIntermediateCriteria(),
        skill.getAdvancedCriteria(), skill.getExpertCriteria());
  }

  private List<BreadcrumbItem> buildBreadcrumb(Skill skill) {
    List<BreadcrumbItem> items = new ArrayList<>();
    for (Skill s = skill; s != null; s = s.getParent()) {
      items.add(new BreadcrumbItem(s.getId(), s.getName()));
    }
    Collections.reverse(items);
    return items;
  }

  @GetMapping(value = "/skills/search", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public List<SkillSearchResult> search(@RequestParam String q) {
      if (q == null || q.isBlank()) {
          return List.of();
      }
    return skillRepository.search(q.strip(), 30).stream()
        .map(s -> new SkillSearchResult(s.getId(), s.getName(),
            truncate(s.getDescription()), s.getPositionCount()))
        .toList();
  }

  public record SkillNodeView(UUID id, String name, String description, String path,
                              Integer positionCount, long childCount,
                              String noviceCriteria, String intermediateCriteria,
                              String advancedCriteria, String expertCriteria) {

    public boolean isGroup() {
      return childCount > 0;
    }
  }

  public record SkillSearchResult(UUID id, String name, String description, Integer positionCount) {

  }

  public record BreadcrumbItem(UUID id, String name) {

  }
}
