package io.github.posseidon.knowledgebase.it.interview.question;

import io.github.posseidon.knowledgebase.it.interview.domain.question.Answer;
import io.github.posseidon.knowledgebase.it.interview.domain.question.Question;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.SkillRepository;
import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HandbookViewController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final SkillRepository skillRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;
    private final QuestionEditService questionEditService;

    public HandbookViewController(SkillRepository skillRepository,
                                  QuestionRepository questionRepository,
                                  QuestionMapper questionMapper,
                                  QuestionEditService questionEditService) {
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
        this.questionEditService = questionEditService;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String scope, Model model) {
        model.addAttribute("scope", scope != null ? scope : "all");
        model.addAttribute("totalQuestions", questionRepository.count());
        return "question/home";
    }

    @Transactional(readOnly = true)
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) UUID skill,
                         @RequestParam(required = false) String scope,
                         Model model) {
        boolean isAsk = q != null && !q.isBlank();
        List<QuestionView> results;
        String displayQuery;

        PageRequest page = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "frequency"));

        if (isAsk) {
            results = questionRepository.findFilteredBySkill(null, q, page)
                    .stream().map(questionMapper::toView).toList();
            displayQuery = q;
        } else if (skill != null) {
            results = questionRepository.findBySkillId(skill, PageRequest.of(0, 50))
                    .stream().map(questionMapper::toView).toList();
            displayQuery = skillRepository.findById(skill).map(Skill::getName).orElse(skill.toString());
        } else {
            results = List.of();
            displayQuery = "";
        }

        results = QuestionScope.filter(results, scope);

        model.addAttribute("query", displayQuery);
        model.addAttribute("hasMatches", !results.isEmpty());
        model.addAttribute("totalCount", results.size());
        model.addAttribute("groups", groupBySkill(results));
        return "question/search";
    }

    @Transactional(readOnly = true)
    @GetMapping("/questions/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<AnswerDetail> answerDetails = question.getAnswers().stream()
                .sorted(Comparator.comparing(Answer::getCreatedAt))
                .map(a -> new AnswerDetail(a.getId(), a.getSource(), a.getContent(),
                        Markdown.toHtml(a.getContent())))
                .toList();

        model.addAttribute("question", questionMapper.toView(question));
        model.addAttribute("questionContentHtml", Markdown.toHtml(question.getContent()));
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("createdAtDisplay", DATE_FMT.format(question.getCreatedAt()));
        return "question/question-detail";
    }

    @PostMapping("/questions/{id}")
    public String updateQuestion(@PathVariable UUID id, @RequestParam String content) {
        questionEditService.updateQuestionContent(id, content);
        return "redirect:/questions/" + id;
    }

    @PostMapping("/questions/{id}/answers")
    public String addAnswer(@PathVariable UUID id, @RequestParam String content) {
        questionEditService.addAnswer(id, content);
        return "redirect:/questions/" + id;
    }

    @PostMapping("/questions/{id}/answers/{answerId}")
    public String updateAnswer(@PathVariable UUID id,
                               @PathVariable UUID answerId,
                               @RequestParam String content) {
        questionEditService.updateAnswer(answerId, content);
        return "redirect:/questions/" + id;
    }

    private List<SkillResultGroup> groupBySkill(List<QuestionView> results) {
        Map<String, List<QuestionView>> bySkill = new LinkedHashMap<>();
        for (QuestionView q : results) {
            String primarySkill = q.skills().isEmpty() ? "Uncategorized" : q.skills().get(0).name();
            bySkill.computeIfAbsent(primarySkill, k -> new ArrayList<>()).add(q);
        }
        return bySkill.entrySet().stream()
                .map(e -> new SkillResultGroup(e.getKey(), e.getValue().size(), e.getValue()))
                .collect(Collectors.toList());
    }

    public record SkillResultGroup(String skillName, int count, List<QuestionView> items) {}
    public record AnswerDetail(UUID id, String source, String rawContent, String htmlContent) {}
}
