package com.kanade.aipassage.common.messaging;

import com.kanade.aipassage.model.dto.ArticleState;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 执行结果回写（ai → article）
 */
@Data
public class SaveResultRequest implements Serializable {

    /** 阶段：TITLE / OUTLINE / CONTENT（TaskMessage 常量） */
    private String phase;

    private List<ArticleState.TitleOption> titleOptions;

    private List<ArticleState.OutlineSection> outlineSections;

    /** 正文（含占位符版本优先） */
    private String content;

    private String fullContent;

    private List<ArticleState.ImageRequirement> imageRequirements;

    private List<ArticleState.ImageResult> images;
}
