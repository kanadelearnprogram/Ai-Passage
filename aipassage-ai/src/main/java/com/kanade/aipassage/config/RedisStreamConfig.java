package com.kanade.aipassage.config;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis Stream 消费配置：创建消费组（幂等）并启动任务监听
 */
/*@Slf4j
@Configuration
public class RedisStreamConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> taskStreamContainer(
            RedisConnectionFactory connectionFactory,
            TaskConsumer taskConsumer,
            StringRedisTemplate stringRedisTemplate) {

        // 消费组不存在则创建（已存在时 Redis 返回 BUSYGROUP，忽略即可）
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    MessagingConstant.TASK_STREAM_KEY, MessagingConstant.TASK_GROUP);
        } catch (Exception e) {
            log.debug("消费组已存在或创建失败（可忽略）: {}", e.getMessage());
        }

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(3))
                        .batchSize(5)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        String consumerName = "ai-" + UUID.randomUUID().toString().substring(0, 8);
        container.receive(
                Consumer.from(MessagingConstant.TASK_GROUP, consumerName),
                StreamOffset.create(MessagingConstant.TASK_STREAM_KEY, ReadOffset.lastConsumed()),
                taskConsumer);
        log.info("Redis Stream 任务监听已注册, consumer={}", consumerName);
        return container;
    }
}*/
