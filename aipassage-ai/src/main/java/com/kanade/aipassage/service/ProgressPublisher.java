package com.kanade.aipassage.service;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import com.kanade.aipassage.common.messaging.ProgressEvent;
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
        // 1. 流式消息：任意 *_STREAMING 枚举按前缀通配（AGENT2_STREAMING/AGENT3_STREAMING/REVIEWER_STREAMING…），
        //    新增流式枚举无需再改这里
        int colonIndex = message.indexOf(':');
        if (colonIndex > 0) {
            String prefix = message.substring(0, colonIndex);
            if (prefix.endsWith("_STREAMING")) {
                try {
                    SseMessageTypeEnum type = SseMessageTypeEnum.valueOf(prefix);
                    return buildStreamingData(type, message.substring(colonIndex + 1));
                } catch (IllegalArgumentException ignored) {
                    // 未知的 *_STREAMING 前缀，继续走后续规则
                }
            }
        }

        // 2. JSON 透传（如章节完成、STEP_UPDATE、IMAGE_COMPLETE 等自带 type 的消息）
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

        // 3. 裸枚举兜底：包装为 {"type": <枚举>} 发出，不再静默丢弃
        if (isKnownEnumValue(message)) {
            return GsonUtils.toJson(Map.of("type", message));
        }
        return null;
    }

    private boolean isKnownEnumValue(String message) {
        for (SseMessageTypeEnum type : SseMessageTypeEnum.values()) {
            if (type.getValue().equals(message)) {
                return true;
            }
        }
        return false;
    }

    private String buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return GsonUtils.toJson(data);
    }
}
