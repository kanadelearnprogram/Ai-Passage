package com.kanade.aipassage.common.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章任务消息（article → ai，走 Redis Stream）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage implements Serializable {

    public static final String PHASE_TITLE = "TITLE";
    public static final String PHASE_OUTLINE = "OUTLINE";
    public static final String PHASE_CONTENT = "CONTENT";

    private String taskId;

    /** 阶段：TITLE / OUTLINE / CONTENT */
    private String phase;
}
