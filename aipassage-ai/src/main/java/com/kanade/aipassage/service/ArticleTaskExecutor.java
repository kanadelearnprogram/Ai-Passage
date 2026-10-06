package com.kanade.aipassage.service;

import com.kanade.aipassage.agent.ArticleAgentOrchestrator;
import com.kanade.aipassage.common.feign.ArticleClient;
import com.kanade.aipassage.common.feign.FeignResponseUtils;
import com.kanade.aipassage.common.messaging.ArticleContextDTO;
import com.kanade.aipassage.common.messaging.SaveResultRequest;
import com.kanade.aipassage.common.messaging.TaskMessage;
import com.kanade.aipassage.model.dto.ArticleState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 任务执行入口：取上下文 → 编排执行 → 回写结果 / 上报失败
 */
@Service
@Slf4j
public class ArticleTaskExecutor {

    @Resource
    private ArticleAgentOrchestrator orchestrator;

    @Resource
    private ProgressPublisher progressPublisher;

    @Resource
    private ArticleClient articleClient;

    public void execute(String taskId, String phase) {
        log.info("开始执行任务, taskId={}, phase={}", taskId, phase);
        ArticleContextDTO context;
        try {
            context = FeignResponseUtils.unwrap(articleClient.getContext(taskId));
        } catch (Exception e) {
            log.error("获取文章上下文失败, taskId={}", taskId, e);
            reportFail(taskId, "获取文章上下文失败");
            return;
        }
        if (context == null) {
            reportFail(taskId, "文章不存在");
            return;
        }

        try {
            SaveResultRequest result = switch (phase) {
                case TaskMessage.PHASE_TITLE -> executeTitle(taskId, context);
                case TaskMessage.PHASE_OUTLINE -> executeOutline(taskId, context);
                case TaskMessage.PHASE_CONTENT -> executeContent(taskId, context);
                default -> throw new IllegalArgumentException("未知任务阶段: " + phase);
            };
            FeignResponseUtils.unwrap(articleClient.saveResult(taskId, result));
            log.info("任务执行完成, taskId={}, phase={}", taskId, phase);
        } catch (Exception e) {
            log.error("任务执行失败, taskId={}, phase={}", taskId, phase, e);
            reportFail(taskId, e.getMessage() == null ? "生成失败" : e.getMessage());
        }
    }

    public void reportFail(String taskId, String message) {
        try {
            articleClient.fail(taskId, message);
        } catch (Exception e) {
            log.error("失败状态上报 article 服务失败, taskId={}", taskId, e);
        }
    }

    private SaveResultRequest executeTitle(String taskId, ArticleContextDTO context) {
        ArticleState state = new ArticleState();
        state.setTaskId(taskId);
        state.setTopic(context.getTopic());
        state.setStyle(context.getStyle());

        orchestrator.executePhase1_GenerateTitles(state,
                msg -> progressPublisher.publish(taskId, msg));

        SaveResultRequest result = new SaveResultRequest();
        result.setPhase(TaskMessage.PHASE_TITLE);
        result.setTitleOptions(state.getTitleOptions());
        return result;
    }

    private SaveResultRequest executeOutline(String taskId, ArticleContextDTO context) {
        ArticleState state = new ArticleState();
        state.setTaskId(taskId);
        state.setStyle(context.getStyle());
        state.setUserDescription(context.getUserDescription());

        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(context.getMainTitle());
        title.setSubTitle(context.getSubTitle());
        state.setTitle(title);

        orchestrator.executePhase2_GenerateOutline(state,
                msg -> progressPublisher.publish(taskId, msg));

        SaveResultRequest result = new SaveResultRequest();
        result.setPhase(TaskMessage.PHASE_OUTLINE);
        result.setOutlineSections(state.getOutline().getSections());
        return result;
    }

    private SaveResultRequest executeContent(String taskId, ArticleContextDTO context) {
        ArticleState state = new ArticleState();
        state.setTaskId(taskId);
        state.setStyle(context.getStyle());
        state.setEnabledImageMethods(context.getEnabledImageMethods());

        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(context.getMainTitle());
        title.setSubTitle(context.getSubTitle());
        state.setTitle(title);

        ArticleState.OutlineResult outlineResult = new ArticleState.OutlineResult();
        outlineResult.setSections(context.getOutlineSections());
        state.setOutline(outlineResult);

        orchestrator.executePhase3_GenerateContent(state,
                msg -> progressPublisher.publish(taskId, msg));

        SaveResultRequest result = new SaveResultRequest();
        result.setPhase(TaskMessage.PHASE_CONTENT);
        result.setContent(state.getContent());
        result.setFullContent(state.getFullContent());
        result.setImageRequirements(state.getImageRequirements());
        result.setImages(state.getImages());
        return result;
    }
}
