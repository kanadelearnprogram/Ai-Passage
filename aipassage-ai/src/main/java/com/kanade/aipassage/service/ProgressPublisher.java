package com.kanade.aipassage.service;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import com.kanade.aipassage.common.messaging.ProgressEvent;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 进度发布者：把 Agent 的原始消息转换为与单体时代一致的 SSE JSON，
 * 经 Redis pub/sub 广播给 article 服务，由其推送给浏览器。
 *
 * 说明：AGENT*_COMPLETE 等阶段完成消息不再由此发布——
 * article 服务在结果落库时统一推送，避免重复。
 */
@Service
@Slf4j
public class ProgressPublisher {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void publish(String taskId, String rawMessage) {
        if (rawMessage == null) {
            return;
        }
        String payload = convert(rawMessage);
        if (payload == null) {
            return;
        }
        ProgressEvent event = new ProgressEvent(taskId, payload);
        stringRedisTemplate.convertAndSend(MessagingConstant.PROGRESS_CHANNEL, GsonUtils.toJson(event));
        log.debug("进度事件已发布, taskId={}, payload={}", taskId, payload);
    }

    private String convert(String message) {
        // 流式正文（带前缀）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();

        if (message.startsWith(streamingPrefix2)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING,
                    message.substring(streamingPrefix2.length()));
        }
        if (message.startsWith(streamingPrefix3)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING,
                    message.substring(streamingPrefix3.length()));
        }
        if (message.startsWith(imageCompletePrefix)) {
            String imageJson = message.substring(imageCompletePrefix.length());
            Map<String, Object> data = new HashMap<>();
            data.put("type", SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
            data.put("image", GsonUtils.fromJson(imageJson, ArticleState.ImageResult.class));
            return GsonUtils.toJson(data);
        }

        // JSON 透传（如章节完成等自带 type 的消息）
        if (message.trim().startsWith("{")) {
            try {
                Map<String, Object> jsonMessage = GsonUtils.fromJson(message, new TypeToken<Map<String, Object>>() {});
                if (jsonMessage != null && jsonMessage.get("type") != null) {
                    return message;
                }
            } catch (Exception e) {
                log.warn("JSON 消息解析失败: {}", message, e);
            }
        }

        // 其余（裸枚举的 *_COMPLETE 等）由 article 侧在结果落库时统一推送
        return null;
    }

    private String buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return GsonUtils.toJson(data);
    }
}
