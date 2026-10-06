package com.kanade.aipassage.common.messaging;

import com.kanade.aipassage.model.dto.ArticleState;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文章上下文（article → ai）：Agent 执行所需的全部输入
 */
@Data
public class ArticleContextDTO implements Serializable {

    private String taskId;

    private Long userId;

    private String topic;

    private String style;

    private String userDescription;

    private String mainTitle;

    private String subTitle;

    private List<ArticleState.OutlineSection> outlineSections;

    private List<String> enabledImageMethods;
}
