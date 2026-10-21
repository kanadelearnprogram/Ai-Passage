package com.kanade.aipassage.agent.agents;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.kanade.aipassage.agent.context.StreamHandlerContext;
import com.kanade.aipassage.constant.PromptConstant;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.utils.GsonUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 内容审核 Agent（质量门禁）
 * 输出结构化结论 {pass, score, issues}，不再覆写正文（正文归 content_generator / content_rewrite 所有）。
 * 配合编排器条件边：不合格 -> content_rewrite 重写后再审，超轮次强制放行。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewerAgent implements NodeAction {

    private final DeepSeekChatModel chatModel;

    public static final String INPUT_CONTENT = "content";
    public static final String OUTPUT_REVIEW_PASS = "reviewPass";
    public static final String OUTPUT_REVIEW_ROUND = "reviewRound";
    public static final String OUTPUT_REVIEW_ISSUES = "reviewIssues";
    public static final String OUTPUT_REVIEW_SCORE = "reviewScore";

    /**
     * 审核结论（LLM 只允许输出该 JSON 结构）
     */
    @Data
    public static class ReviewResult {
        private boolean pass;
        private int score;
        private List<String> issues;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String content = state.value(INPUT_CONTENT)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少输入内容参数"));
        int round = state.value(OUTPUT_REVIEW_ROUND)
                .map(v -> Integer.parseInt(v.toString()))
                .orElse(0) + 1;

        Consumer<String> streamHandler = StreamHandlerContext.get();
        emitEvent(streamHandler, Map.of(
                "type", SseMessageTypeEnum.REVIEW_START.getValue(),
                "round", round));
        log.info("ReviewerAgent 开始审校: round={}, 正文长度={}", round, content.length());

        String prompt = PromptConstant.REVIEWER_PROMPT.replace("{content}", content);
        String responseText = chatModel.call(new Prompt(prompt))
                .getResult().getOutput().getText();

        ReviewResult reviewResult = parseReviewResult(responseText);
        if (reviewResult == null) {
            // 解析失败时按通过处理：审核是建议性的，不能因输出格式问题卡死创作流程
            reviewResult = new ReviewResult();
            reviewResult.setPass(true);
            reviewResult.setScore(-1);
            reviewResult.setIssues(List.of("本轮审核结果解析失败，已自动跳过"));
        }
        List<String> issues = reviewResult.getIssues() == null ? List.of() : reviewResult.getIssues();

        emitEvent(streamHandler, Map.of(
                "type", SseMessageTypeEnum.REVIEW_ROUND.getValue(),
                "round", round,
                "pass", reviewResult.isPass(),
                "score", reviewResult.getScore(),
                "issues", issues));
        log.info("ReviewerAgent 审校完成: round={}, pass={}, score={}, issues={}",
                round, reviewResult.isPass(), reviewResult.getScore(), issues.size());

        // 注意：不返回 content，正文保持归 content_generator / content_rewrite 所有
        return Map.of(
                OUTPUT_REVIEW_PASS, reviewResult.isPass(),
                OUTPUT_REVIEW_ROUND, round,
                OUTPUT_REVIEW_ISSUES, issues,
                OUTPUT_REVIEW_SCORE, reviewResult.getScore());
    }

    /**
     * 解析审核结论：容错剥离代码块围栏并截取首个 JSON 片段；失败返回 null
     */
    private ReviewResult parseReviewResult(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String json = text.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return GsonUtils.fromJson(json, ReviewResult.class);
        } catch (Exception e) {
            log.warn("审核结论 JSON 解析失败: {}", text, e);
            return null;
        }
    }

    private void emitEvent(Consumer<String> streamHandler, Map<String, Object> data) {
        if (streamHandler == null) {
            return;
        }
        try {
            streamHandler.accept(GsonUtils.toJson(data));
        } catch (Exception e) {
            log.warn("审核事件推送失败", e);
        }
    }
}
