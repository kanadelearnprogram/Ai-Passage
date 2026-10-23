package com.kanade.aipassage.service;

import com.google.gson.JsonSyntaxException;
import com.kanade.aipassage.annotation.AgentExecution;
import com.kanade.aipassage.constant.PromptConstant;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 独立 LLM 调用服务（原多阶段实现已由 ArticleAgentOrchestrator + 任务管道取代）
 */
@Service
@Slf4j
public class ArticleAgentService {

    @Resource
    private DeepSeekChatModel chatModel;

    /**
     * AI 修改大纲
     */
    @AgentExecution(value = "ai_modify_outline", description = "AI 修改大纲")
    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle, String subTitle,
                                                             List<ArticleState.OutlineSection> current,
                                                             String modifySuggestion) {
        String currentOutlineJson = GsonUtils.toJson(current);

        String prompt = PromptConstant.AI_MODIFY_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{currentOutline}", currentOutlineJson)
                .replace("{modifySuggestion}", modifySuggestion);

        String content = callLlm(prompt);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "修改后的大纲");

        log.info("AI修改大纲成功, sectionsCount={}", outlineResult.getSections().size());
        return outlineResult.getSections();
    }

    private String callLlm(String prompt) {
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }
}
