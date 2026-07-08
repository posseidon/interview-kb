package io.github.posseidon.knowledgebase.it.interview.web;

import io.github.posseidon.knowledgebase.it.interview.dto.QuestionView;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.TagRepository;
import io.github.posseidon.knowledgebase.it.interview.repo.TopicRepository;
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

    private final TopicRepository topicRepository;
    private final TagRepository tagRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public ListingController(TopicRepository topicRepository, TagRepository tagRepository,
                             QuestionRepository questionRepository, QuestionMapper questionMapper) {
        this.topicRepository = topicRepository;
        this.tagRepository = tagRepository;
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<TopicDto>> getTopics(@RequestParam(required = false) String q) {
        List<TopicDto> topics = (q == null || q.isBlank()
                ? topicRepository.findAll()
                : topicRepository.search(q))
                .stream()
                .map(t -> new TopicDto(t.getId(), t.getSlug(), t.getName(), t.getDescription()))
                .toList();
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagDto>> getTags(@RequestParam(required = false) String q) {
        List<TagDto> tags = (q == null || q.isBlank()
                ? tagRepository.findAll()
                : tagRepository.findByNameContainingIgnoreCase(q))
                .stream()
                .map(t -> new TagDto(t.getId(), t.getName()))
                .toList();
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/questions")
    public ResponseEntity<Page<QuestionView>> getQuestions(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "frequency", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(questionRepository.findFiltered(topic, tag, q, pageable)
                .map(questionMapper::toView));
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<QuestionView> getQuestion(@PathVariable UUID id) {
        return questionRepository.findById(id)
                .map(questionMapper::toView)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/topics/{slug}/questions")
    public ResponseEntity<List<QuestionView>> getQuestionsByTopic(
            @PathVariable String slug,
            @PageableDefault(size = 20, sort = "frequency", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(questionRepository.findByTopicSlug(slug, pageable)
                .stream().map(questionMapper::toView).toList());
    }

    public record TopicDto(UUID id, String slug, String name, String description) {}
    public record TagDto(UUID id, String name) {}
}
