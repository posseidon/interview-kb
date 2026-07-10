package io.github.posseidon.knowledgebase.it.interview.basket;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/basket")
public class BasketController {

    private final Basket basket;
    private final SkillRepository skillRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public BasketController(Basket basket, SkillRepository skillRepository,
                            QuestionRepository questionRepository, QuestionMapper questionMapper) {
        this.basket = basket;
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @Transactional(readOnly = true)
    @PostMapping("/items")
    public String addItem(@RequestParam UUID skillId, @RequestParam SkillLevel level) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        basket.put(skillId, level);
        Skill parent = skill.getParent();
        return "redirect:/skills/" + (parent != null ? parent.getId() : skillId);
    }

    @PostMapping("/items/{skillId}/remove")
    public String removeItem(@PathVariable UUID skillId) {
        basket.remove(skillId);
        return "redirect:/basket";
    }

    @PostMapping("/clear")
    public String clear() {
        basket.clear();
        return "redirect:/basket";
    }

    @Transactional(readOnly = true)
    @GetMapping
    public String view(Model model) {
        Map<UUID, Skill> skillsById = skillsById(basket.items().keySet());
        List<BasketItemView> items = basket.items().entrySet().stream()
                .map(e -> {
                    Skill skill = skillsById.get(e.getKey());
                    return skill == null ? null
                            : new BasketItemView(skill.getId(), skill.getName(), skill.getPath(), e.getValue());
                })
                .filter(Objects::nonNull)
                .toList();
        model.addAttribute("items", items);
        model.addAttribute("isEmpty", items.isEmpty());
        return "basket/basket";
    }

    private Map<UUID, Skill> skillsById(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return skillRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
    }

    @Transactional(readOnly = true)
    @PostMapping("/checkout")
    public String checkout(Model model) {
        Map<UUID, Skill> skillsById = skillsById(basket.items().keySet());
        Map<UUID, QuestionView> byId = new LinkedHashMap<>();
        List<SkillResultGroup> groups = new ArrayList<>();

        for (Map.Entry<UUID, SkillLevel> entry : basket.items().entrySet()) {
            Skill skill = skillsById.get(entry.getKey());
            if (skill == null) continue;
            SkillLevel level = entry.getValue();

            List<QuestionView> matched = questionRepository.findBySkillIdAndLevel(skill.getId(), level.name())
                    .stream()
                    .map(q -> byId.computeIfAbsent(q.getId(), id -> questionMapper.toView(q)))
                    .toList();
            groups.add(new SkillResultGroup(skill.getName(), level, matched));
        }

        model.addAttribute("groups", groups);
        model.addAttribute("totalQuestions", byId.size());
        basket.clear();
        return "basket/checkout";
    }

    public record BasketItemView(UUID skillId, String skillName, String skillPath, SkillLevel level) {}

    public record SkillResultGroup(String skillName, SkillLevel level, List<QuestionView> questions) {}
}
