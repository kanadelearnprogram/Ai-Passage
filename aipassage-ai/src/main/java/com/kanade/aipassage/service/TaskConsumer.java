package com.kanade.aipassage.service;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

/**
 * 任务消费者：从 Redis Stream 读取文章任务，执行后手动 ACK
 */
/*
@Service
@Slf4j
public class TaskConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    @Resource
    private ArticleTaskExecutor articleTaskExecutor;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String taskId = record.getValue().get("taskId");
        String phase = record.getValue().get("phase");
        try {
            if (taskId == null || phase == null) {
                log.warn("非法任务消息, record={}", record);
            } else {
                articleTaskExecutor.execute(taskId, phase);
            }
        } catch (Exception e) {
            // executor 内部已上报失败，这里兜底记录，保证消息被 ACK 不会无限重投
            log.error("任务消息处理异常, taskId={}", taskId, e);
        } finally {
            stringRedisTemplate.opsForStream().acknowledge(
                    MessagingConstant.TASK_STREAM_KEY, MessagingConstant.TASK_GROUP, record.getId());
        }
    }
}
*/
