package com.kanade.aipassage.sse;

import com.kanade.aipassage.common.messaging.ProgressEvent;
import com.kanade.aipassage.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 进度订阅者：接收 ai 服务发布的进度事件，转发给本实例持有的 SSE 连接。
 * 多实例部署时事件会广播到所有实例，只有持有该 taskId 连接的实例真正推送。
 */
@Service
@Slf4j
public class ProgressSubscriber implements MessageListener {

    private final StringRedisTemplate stringRedisTemplate;

    private final SseEmitterManager sseEmitterManager;

    public ProgressSubscriber(StringRedisTemplate stringRedisTemplate, SseEmitterManager sseEmitterManager) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.sseEmitterManager = sseEmitterManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = stringRedisTemplate.getStringSerializer().deserialize(message.getBody());
            ProgressEvent event = GsonUtils.fromJson(body, ProgressEvent.class);
            if (event == null || event.getTaskId() == null) {
                return;
            }
            sseEmitterManager.send(event.getTaskId(), event.getPayload());
        } catch (Exception e) {
            log.error("进度事件处理失败", e);
        }
    }
}
