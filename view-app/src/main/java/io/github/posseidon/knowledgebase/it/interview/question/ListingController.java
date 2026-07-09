package io.github.posseidon.knowledgebase.it.interview.question;

import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.util.QuestionMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Transactional(readOnly = true)
public class ListingController {

    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public ListingController(QuestionRepository questionRepository, QuestionMapper questionMapper) {
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @GetMapping("/questions")
    public ResponseEntity<Page<QuestionView>> getQuestions(
            @RequestParam(required = false) UUID skill,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "frequency", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(questionRepository.findFilteredBySkill(skill, q, pageable)
                .map(questionMapper::toView));
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<QuestionView> getQuestion(@PathVariable UUID id) {
        return questionRepository.findById(id)
                .map(questionMapper::toView)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/skills/{id}/questions")
    public ResponseEntity<List<QuestionView>> getQuestionsBySkill(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "frequency", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(questionRepository.findBySkillId(id, pageable)
                .stream().map(questionMapper::toView).toList());
    }

}
