package com.kanade.aipassage.config;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import com.kanade.aipassage.sse.ProgressSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub 订阅配置：订阅 Agent 进度事件通道
 */
@Configuration
public class RedisSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer progressListenerContainer(RedisConnectionFactory connectionFactory,
                                                                   ProgressSubscriber progressSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(progressSubscriber, new ChannelTopic(MessagingConstant.PROGRESS_CHANNEL));
        return container;
    }
}
