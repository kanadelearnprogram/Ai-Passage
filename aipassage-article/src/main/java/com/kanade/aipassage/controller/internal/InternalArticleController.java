package com.kanade.aipassage.controller.internal;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.common.messaging.ArticleContextDTO;
import com.kanade.aipassage.common.messaging.SaveResultRequest;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.entity.Article;
import com.kanade.aipassage.service.ArticleResultHandler;
import com.kanade.aipassage.service.ArticleService;
import com.kanade.aipassage.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章服务内部接口（仅服务间调用，same-token 防护，不暴露到网关）
 */
@RestController
@RequestMapping("/internal/article")
@Tag(name = "InternalArticleController", description = "文章服务内部接口")
public class InternalArticleController {

    @Resource
    private ArticleService articleService;

    @Resource
    private ArticleResultHandler articleResultHandler;

    /**
     * 获取 Agent 执行上下文
     */
    @GetMapping("/{taskId}/context")
    @Operation(summary = "获取文章上下文")
    public BaseResponse<ArticleContextDTO> getContext(@PathVariable String taskId) {
        Article article = articleService.getByTaskId(taskId);
        ArticleContextDTO context = new ArticleContextDTO();
        context.setTaskId(taskId);
        context.setUserId(article.getUserId());
        context.setTopic(article.getTopic());
        context.setStyle(article.getStyle());
        context.setUserDescription(article.getUserDescription());
        context.setMainTitle(article.getMainTitle());
        context.setSubTitle(article.getSubTitle());
        if (article.getOutline() != null) {
            List<ArticleState.OutlineSection> sections = GsonUtils.fromJson(
                    article.getOutline(), new TypeToken<List<ArticleState.OutlineSection>>() {});
            context.setOutlineSections(sections);
        }
        if (article.getEnabledImageMethods() != null) {
            context.setEnabledImageMethods(GsonUtils.fromJson(
                    article.getEnabledImageMethods(), new TypeToken<List<String>>() {}));
        }
        return ResultUtils.success(context);
    }

    /**
     * 回写阶段结果
     */
    @PostMapping("/{taskId}/result")
    @Operation(summary = "回写阶段结果")
    public BaseResponse<Boolean> saveResult(@PathVariable String taskId, @RequestBody SaveResultRequest request) {
        articleResultHandler.handleResult(taskId, request);
        return ResultUtils.success(true);
    }

    /**
     * 标记任务失败
     */
    @PostMapping("/{taskId}/fail")
    @Operation(summary = "标记任务失败")
    public BaseResponse<Boolean> fail(@PathVariable String taskId, @RequestBody String message) {
        articleResultHandler.handleFail(taskId, message);
        return ResultUtils.success(true);
    }
}
