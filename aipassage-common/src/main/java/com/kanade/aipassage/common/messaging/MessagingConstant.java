package com.kanade.aipassage.common.messaging;

/**
 * 消息管道常量（Redis Stream / pub-sub / 幂等键）
 */
public interface MessagingConstant {

    /** 文章任务队列（Redis Stream） */
    String TASK_STREAM_KEY = "aipassage:task:stream";

    /** 消费组 */
    String TASK_GROUP = "aipassage-agent-group";

    /** Agent 进度事件通道（Redis pub/sub） */
    String PROGRESS_CHANNEL = "aipassage:progress";

    /** 配额扣减幂等标记前缀（后接 taskId） */
    String QUOTA_TASK_PREFIX = "aipassage:quota:task:";
}
