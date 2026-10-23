package com.kanade.aipassage.agent.agents;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.kanade.aipassage.constant.PromptConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 正文重写 Agent（审核质量门禁的"不合格返工"节点）
 * 输入正文 + 审校意见，输出重写后的正文（写回 content 键），重写后回到 reviewer 再审。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentRewriteAgent implements NodeAction {

    private final DeepSeekChatModel chatModel;

    public static final String INPUT_CONTENT = "content";
    public static final String INPUT_REVIEW_ISSUES = "reviewIssues";
    public static final String OUTPUT_CONTENT = "content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String content = state.value(INPUT_CONTENT)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少输入内容参数"));
        String issuesText = state.value(INPUT_REVIEW_ISSUES)
                .map(v -> {
                    if (v instanceof List<?> list) {
                        return list.stream().map(Object::toString).collect(Collectors.joining("\n- "));
                    }
                    return v.toString();
                })
                .orElse("");

        log.info("ContentRewriteAgent 开始重写: 正文长度={}", content.length());

        String prompt = PromptConstant.CONTENT_REWRITE_PROMPT
                .replace("{issues}", "- " + issuesText)
                .replace("{content}", content);
        String rewritten = chatModel.call(new Prompt(prompt))
                .getResult().getOutput().getText();

        String cleaned = stripCodeFence(rewritten);
        log.info("ContentRewriteAgent 重写完成: 新正文长度={}", cleaned.length());
        return Map.of(OUTPUT_CONTENT, cleaned);
    }

    /**
     * 容错：剥离模型可能输出的代码块围栏
     */
    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        return cleaned;
    }
}
