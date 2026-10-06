package com.kanade.aipassage.common.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent 进度事件（ai → article，走 Redis pub/sub）
 * payload 即最终推给浏览器的 SSE JSON 串，article 原样转发，
 * 保证与单体时代的消息格式逐字节一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressEvent implements Serializable {

    private String taskId;

    private String payload;
}
