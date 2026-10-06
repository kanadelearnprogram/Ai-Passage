package com.kanade.aipassage.service;

import com.kanade.aipassage.common.feign.UserClient;
import com.kanade.aipassage.common.feign.FeignResponseUtils;
import com.kanade.aipassage.common.messaging.QuotaRequest;
import com.kanade.aipassage.common.messaging.SaveResultRequest;
import com.kanade.aipassage.common.messaging.TaskMessage;
import com.kanade.aipassage.model.entity.Article;
import com.kanade.aipassage.model.enums.ArticleStatusEnum;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.sse.SseEmitterManager;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 结果处理：推进状态机 + 按单体时代的顺序推送阶段完成 SSE 消息
 */
@Service
@Slf4j
public class ArticleResultHandler {

    @Resource
    private ArticleService articleService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private UserClient userClient;

    public void handleResult(String taskId, SaveResultRequest request) {
        switch (request.getPhase()) {
            case TaskMessage.PHASE_TITLE -> handleTitle(taskId, request);
            case TaskMessage.PHASE_OUTLINE -> handleOutline(taskId, request);
            case TaskMessage.PHASE_CONTENT -> handleContent(taskId, request);
            default -> log.warn("未知的任务阶段, taskId={}, phase={}", taskId, request.getPhase());
        }
    }

    private void handleTitle(String taskId, SaveResultRequest request) {
        articleService.saveTitleOptions(taskId, request.getTitleOptions());
        articleService.updatePhase(taskId, com.kanade.aipassage.model.enums.ArticlePhaseEnum.TITLE_SELECTING);

        sendSse(taskId, SseMessageTypeEnum.AGENT1_COMPLETE, new HashMap<>());
        Map<String, Object> data = new HashMap<>();
        data.put("titleOptions", request.getTitleOptions());
        sendSse(taskId, SseMessageTypeEnum.TITLES_GENERATED, data);
        log.info("阶段1结果处理完成, taskId={}", taskId);
    }

    private void handleOutline(String taskId, SaveResultRequest request) {
        articleService.saveOutline(taskId, request.getOutlineSections());
        articleService.updatePhase(taskId, com.kanade.aipassage.model.enums.ArticlePhaseEnum.OUTLINE_EDITING);

        Map<String, Object> completeData = new HashMap<>();
        completeData.put("outline", request.getOutlineSections());
        sendSse(taskId, SseMessageTypeEnum.AGENT2_COMPLETE, completeData);

        Map<String, Object> generatedData = new HashMap<>();
        generatedData.put("outline", request.getOutlineSections());
        sendSse(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, generatedData);
        log.info("阶段2结果处理完成, taskId={}", taskId);
    }

    private void handleContent(String taskId, SaveResultRequest request) {
        articleService.saveContent(taskId, request);
        articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);

        sendSse(taskId, SseMessageTypeEnum.AGENT3_COMPLETE, new HashMap<>());
        Map<String, Object> agent4 = new HashMap<>();
        agent4.put("imageRequirements", request.getImageRequirements());
        sendSse(taskId, SseMessageTypeEnum.AGENT4_COMPLETE, agent4);
        Map<String, Object> agent5 = new HashMap<>();
        agent5.put("images", request.getImages());
        sendSse(taskId, SseMessageTypeEnum.AGENT5_COMPLETE, agent5);
        Map<String, Object> merge = new HashMap<>();
        merge.put("fullContent", request.getFullContent());
        sendSse(taskId, SseMessageTypeEnum.MERGE_COMPLETE, merge);
        Map<String, Object> all = new HashMap<>();
        all.put("taskId", taskId);
        sendSse(taskId, SseMessageTypeEnum.ALL_COMPLETE, all);

        // 全部完成，关闭 SSE 连接
        sseEmitterManager.complete(taskId);
        log.info("阶段3结果处理完成, taskId={}", taskId);
    }

    /**
     * 任务失败：标记 FAILED → 退还配额 → 推送错误 → 关闭 SSE
     */
    public void handleFail(String taskId, String message) {
        Article article = articleService.getByTaskId(taskId);
        articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, message);
        if (article != null && article.getUserId() != null) {
            try {
                FeignResponseUtils.unwrap(userClient.refundQuota(new QuotaRequest(article.getUserId(), taskId)));
            } catch (Exception e) {
                log.error("配额退还失败, taskId={}", taskId, e);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("message", message == null ? "生成失败" : message);
        sendSse(taskId, SseMessageTypeEnum.ERROR, data);
        sseEmitterManager.complete(taskId);
        log.info("任务失败处理完成, taskId={}", taskId);
    }

    private void sendSse(String taskId, SseMessageTypeEnum type, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        if (additionalData != null) {
            data.putAll(additionalData);
        }
        sseEmitterManager.send(taskId, GsonUtils.toJson(data));
    }
}
