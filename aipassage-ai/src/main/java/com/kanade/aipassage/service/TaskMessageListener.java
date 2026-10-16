package com.kanade.aipassage.service;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import com.kanade.aipassage.common.messaging.TaskMessage;
import com.kanade.aipassage.exception.BusinessException;
import com.kanade.aipassage.exception.ErrorCode;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import static com.kanade.aipassage.utils.GsonUtils.fromJson;


@Service
@Slf4j
@RocketMQMessageListener(
        topic = MessagingConstant.TASK_TOPIC,
        consumerGroup = MessagingConstant.TASK_GROUP,
        maxReconsumeTimes = 3)
public class TaskMessageListener implements RocketMQListener<String> {

    @Resource
    ArticleTaskExecutor executor;

    @Override
    public void onMessage(String message) {
        TaskMessage task = GsonUtils.fromJson(message, TaskMessage.class);

        if (task == null || task.getPhase() == null ||task.getTaskId() == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息队列任务为空");
        }
        executor.execute(task.getTaskId(), task.getPhase());
    }
}
