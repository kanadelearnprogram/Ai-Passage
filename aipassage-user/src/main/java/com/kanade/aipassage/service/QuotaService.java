package com.kanade.aipassage.service;

import com.kanade.aipassage.common.messaging.UserStatsDTO;

/**
 * 配额服务：跨服务幂等扣减/退还 + 用户侧统计
 */
public interface QuotaService {

    /**
     * 校验并扣减 1 次创作配额（以 taskId 幂等）
     */
    boolean deduct(Long userId, String taskId);

    /**
     * 退还配额（任务失败补偿，以 taskId 幂等）
     */
    boolean refund(Long userId, String taskId);

    /**
     * 用户侧统计（总数 / VIP 数 / 配额使用量）
     */
    UserStatsDTO getStats();
}
