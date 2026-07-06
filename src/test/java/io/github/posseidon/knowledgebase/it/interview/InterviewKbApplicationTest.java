package io.github.posseidon.knowledgebase.it.interview;

import io.github.posseidon.knowledgebase.it.interview.domain.Question;
import io.github.posseidon.knowledgebase.it.interview.dto.*;
import io.github.posseidon.knowledgebase.it.interview.ingest.IngestionService;
import io.github.posseidon.knowledgebase.it.interview.repo.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InterviewKbApplicationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("interview_kb")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void testIngestionHappyPath() {
        // Create test data
        TopicDto topicDto = new TopicDto("kafka", "Kafka", "Event streaming basics.");
        AnswerDto answerDto = new AnswerDto("human",
                "A topic is an append-only log; partitions are the unit of parallelism and ordering.");
        QuestionDto questionDto = new QuestionDto(
                "kafka-topic-vs-partition",
                "What is a topic? What is a partition?",
                false,
                "en",
                List.of("kafka"),
                List.of("kafka", "fundamentals"),
                List.of(answerDto)
        );
        IngestRequest request = new IngestRequest(
                List.of(topicDto),
                List.of(questionDto)
        );

        // Ingest
        IngestResponse response = ingestionService.ingest(request);

        // Verify
        assertThat(response.questionsCreated()).isEqualTo(1);
        assertThat(response.answersAdded()).isEqualTo(1);

        Optional<Question> saved = questionRepository.findByExternalId("kafka-topic-vs-partition");
        assertThat(saved).isPresent();

        Question q = saved.get();
        assertThat(q.getContent()).isEqualTo("What is a topic? What is a partition?");
        assertThat(q.getTopics()).hasSize(1);
        assertThat(q.getTags()).hasSize(2);
        assertThat(q.getAnswers()).hasSize(1);

        // Ingest again (idempotent)
        IngestResponse response2 = ingestionService.ingest(request);
        assertThat(response2.questionsCreated()).isEqualTo(0);
        assertThat(response2.questionsUpdated()).isEqualTo(1);
        assertThat(response2.answersAdded()).isEqualTo(0);

        // Verify vector store has exactly one row per question
        List<Question> allQuestions = questionRepository.findAll();
        assertThat(allQuestions).hasSize(1);
    }
}
