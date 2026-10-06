package com.kanade.aipassage.service;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import com.kanade.aipassage.common.messaging.TaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 任务生产者：把 Agent 阶段任务投递到 Redis Stream（article → ai）
 */
@Service
@Slf4j
public class TaskProducer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void sendPhase(String taskId, String phase) {
        stringRedisTemplate.opsForStream().add(
                MessagingConstant.TASK_STREAM_KEY,
                Map.of("taskId", taskId, "phase", phase)
        );
        log.info("任务消息已投递, taskId={}, phase={}", taskId, phase);
    }
}
