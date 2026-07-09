package io.github.posseidon.knowledgebase.it.interview.skill;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@Transactional(readOnly = true)
public class SkillController {

    private final SkillRepository skillRepository;

    public SkillController(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
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

    // One batched COUNT ... GROUP BY instead of one query per skill.
    private Map<UUID, Long> childCounts(List<Skill> skills) {
        if (skills.isEmpty()) return Map.of();
        List<UUID> ids = skills.stream().map(Skill::getId).toList();
        return skillRepository.countChildrenByParentIds(ids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    @GetMapping(value = "/skills/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<SkillSearchResult> search(@RequestParam String q) {
        if (q == null || q.isBlank()) return List.of();
        return skillRepository.search(q.strip(), 30).stream()
                .map(s -> new SkillSearchResult(s.getId(), s.getName(),
                        truncate(s.getDescription(), 200), s.getPositionCount()))
                .toList();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength).strip() + "…";
    }

    private List<BreadcrumbItem> buildBreadcrumb(Skill skill) {
        List<BreadcrumbItem> items = new ArrayList<>();
        for (Skill s = skill; s != null; s = s.getParent()) {
            items.add(new BreadcrumbItem(s.getId(), s.getName()));
        }
        Collections.reverse(items);
        return items;
    }

    private SkillNodeView toView(Skill skill) {
        return new SkillNodeView(skill.getId(), skill.getName(), skill.getDescription(),
                skill.getPath(), skill.getPositionCount(), 0,
                skill.getNoviceCriteria(), skill.getIntermediateCriteria(),
                skill.getAdvancedCriteria(), skill.getExpertCriteria());
    }

    public record SkillNodeView(UUID id, String name, String description, String path,
                                 Integer positionCount, long childCount,
                                 String noviceCriteria, String intermediateCriteria,
                                 String advancedCriteria, String expertCriteria) {
        public boolean isGroup() {
            return childCount > 0;
        }
    }

    public record SkillSearchResult(UUID id, String name, String description, Integer positionCount) {}

    public record BreadcrumbItem(UUID id, String name) {}
}
