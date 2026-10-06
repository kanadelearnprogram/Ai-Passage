package com.kanade.aipassage.service;

import com.google.gson.reflect.TypeToken;
import com.kanade.aipassage.agent.ArticleAgentOrchestrator;
import com.kanade.aipassage.config.AgentConfig;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.entity.Article;
import com.kanade.aipassage.model.enums.ArticlePhaseEnum;
import com.kanade.aipassage.model.enums.ArticleStatusEnum;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.sse.SseEmitterManager;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class ArticleAsyncService {

    @Resource
    private ArticleAgentOrchestrator articleAgentOrchestrator;

    @Resource
    private AgentConfig agentConfig;

    @Resource
    private ArticleAgentService articleAgentService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private ArticleService articleService;



    //阶段1：异步生成标题方案
    @Async("articleExecutor")
    public void executePhase1(String taskId, String topic, String style) {

        boolean orchestratorEnabled = agentConfig.isOrchestratorEnabled();

        log.info("阶段1异步任务开始, taskId={}, topic={}, style={},agent={}", taskId, topic, style, orchestratorEnabled);

        try {
            // 更新状态和阶段
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.PROCESSING, null);
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_GENERATING);

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);

            // 执行阶段1：生成标题方案
            if (orchestratorEnabled){
                articleAgentOrchestrator.executePhase1_GenerateTitles(state, message ->{
                    handleAgentMessage(taskId,message,state);
                });
            }else {
                articleAgentService.executePhase1_GenerateTitles(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 保存标题方案到数据库
            articleService.saveTitleOptions(taskId, state.getTitleOptions());

            // 更新阶段为等待选择标题
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);

            // 推送标题方案生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("titleOptions", state.getTitleOptions());
            sendSseMessage(taskId, SseMessageTypeEnum.TITLES_GENERATED, data);

            log.info("阶段1异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段1异步任务失败, taskId={}", taskId, e);

            // 更新状态为失败
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());

            // 推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));

            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 阶段2：异步生成大纲（用户确认标题后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase2(String taskId) {
        boolean orchestratorEnabled = agentConfig.isOrchestratorEnabled();

        log.info("阶段2异步任务开始, taskId={}", taskId);

        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());
            state.setUserDescription(article.getUserDescription());

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 执行阶段2：生成大纲
            if (orchestratorEnabled){
                articleAgentOrchestrator.executePhase2_GenerateOutline(state, message ->{
                    handleAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase2_GenerateOutline(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 保存大纲到数据库
            Article articleToUpdate = articleService.getByTaskId(taskId);
            articleToUpdate.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
            articleService.updateById(articleToUpdate);

            // 更新阶段为等待编辑大纲
            articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);

            // 推送大纲生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("outline", state.getOutline().getSections());
            log.info("准备推送 OUTLINE_GENERATED 消息, taskId={}, outline 大小={}", 
                taskId, state.getOutline().getSections().size());
            sendSseMessage(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, data);
            log.info("OUTLINE_GENERATED 消息已推送, taskId={}", taskId);

            log.info("阶段2异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段2异步任务失败, taskId={}", taskId, e);

            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 阶段3：异步生成正文+配图（用户确认大纲后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase3(String taskId) {
        boolean orchestratorEnabled = agentConfig.isOrchestratorEnabled();
        log.info("阶段3异步任务开始, taskId={}", taskId);

        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());

            // 从数据库获取允许的配图方式
            List<String> enabledMethods = null;
            if (article.getEnabledImageMethods() != null) {
                enabledMethods = GsonUtils.fromJson(
                        article.getEnabledImageMethods(),
                        new TypeToken<List<String>>(){}
                );
            }
            state.setEnabledImageMethods(enabledMethods);

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 设置大纲
            List<ArticleState.OutlineSection> outlineSections = GsonUtils.fromJson(
                    article.getOutline(),
                    new TypeToken<List<ArticleState.OutlineSection>>(){}
            );
            ArticleState.OutlineResult outlineResult = new ArticleState.OutlineResult();
            outlineResult.setSections(outlineSections);
            state.setOutline(outlineResult);

            // 执行阶段3：生成正文+配图
            if (orchestratorEnabled){
                articleAgentOrchestrator.executePhase3_GenerateContent(state, message ->{
                    handleAgentMessage(taskId, message, state);
                });
            }else {
                articleAgentService.executePhase3_GenerateContent(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 保存完整文章到数据库
            articleService.saveArticleContent(taskId, state);

            // 更新状态为已完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);

            // 推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));

            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);

            log.info("阶段3异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段3异步任务失败, taskId={}", taskId, e);

            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            sseEmitterManager.complete(taskId);
        }
    }


    private void handleAgentMessage(String taskId,String message, ArticleState state){
        log.info("收到 Agent 消息: message={}, taskId={}", message, taskId);
        Map<String,Object> data = buildMessageData(message,state);
        if (data != null){
            log.info("准备发送 SSE 消息: type={}, taskId={}", data.get("type"), taskId);
            sseEmitterManager.send(taskId,GsonUtils.toJson(data));
            log.info("SSE 消息已发送: type={}, taskId={}", data.get("type"), taskId);
        } else {
            log.warn("buildMessageData 返回 null, message={}, taskId={}", message, taskId);
        }
    }

    private Map<String, Object> buildMessageData(String message, ArticleState state) {
        // 处理流式消息（带冒号分隔符）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();

        if (message.startsWith(streamingPrefix2)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING, message.substring(streamingPrefix2.length()));
        }

        if (message.startsWith(streamingPrefix3)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING, message.substring(streamingPrefix3.length()));
        }

        if (message.startsWith(imageCompletePrefix)) {
            String imageJson = message.substring(imageCompletePrefix.length());
            return buildImageCompleteData(imageJson);
        }

        // 尝试解析 JSON 格式的消息（如 CHAPTER_COMPLETE）
        if (message.trim().startsWith("{")) {
            try {
                Map<String, Object> jsonMessage = GsonUtils.fromJson(message, new TypeToken<Map<String, Object>>(){});
                String type = (String) jsonMessage.get("type");
                if (type != null) {
                    log.info("解析到 JSON 格式消息: type={}", type);
                    // 直接返回 JSON 消息，让前端处理
                    return jsonMessage;
                }
            } catch (Exception e) {
                log.warn("JSON 消息解析失败: {}", message, e);
            }
        }

        // 处理完成消息（枚举值）
        return buildCompleteMessageData(message, state);
    }

    private Map<String, Object> buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return data;
    }

    private Map<String, Object> buildImageCompleteData(String imageJson) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
        data.put("image", GsonUtils.fromJson(imageJson, ArticleState.ImageResult.class));
        return data;
    }

    /**
     * 构建完成消息数据
     */
    private Map<String, Object> buildCompleteMessageData(String message, ArticleState state) {
        Map<String, Object> data = new HashMap<>();

        // 使用枚举值匹配
        if (SseMessageTypeEnum.AGENT1_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            data.put("title", state.getTitle());
        } else if (SseMessageTypeEnum.AGENT2_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            data.put("outline", state.getOutline().getSections());
        } else if (SseMessageTypeEnum.AGENT3_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
        } else if (SseMessageTypeEnum.AGENT4_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            data.put("imageRequirements", state.getImageRequirements());
        } else if (SseMessageTypeEnum.AGENT5_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            data.put("images", state.getImages());
        } else if (SseMessageTypeEnum.MERGE_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            data.put("fullContent", state.getFullContent());
        } else {
            return null;
        }

        return data;
    }

    private void sendSseMessage(String taskId, SseMessageTypeEnum type, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.putAll(additionalData);
        String jsonMessage = GsonUtils.toJson(data);
        log.debug("发送 SSE 消息: type={}, json={}", type.getValue(), jsonMessage);
        sseEmitterManager.send(taskId, jsonMessage);
    }

}
