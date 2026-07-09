package io.github.posseidon.knowledgebase.it.interview.config;

import io.github.posseidon.knowledgebase.it.interview.mcp.QuestionSearchMcpTool;
import io.github.posseidon.knowledgebase.it.interview.mcp.SkillQuestionMcpTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpTools(SkillQuestionMcpTool skillQuestionMcpTool,
                                         QuestionSearchMcpTool questionSearchMcpTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(skillQuestionMcpTool, questionSearchMcpTool)
                .build();
    }
}
