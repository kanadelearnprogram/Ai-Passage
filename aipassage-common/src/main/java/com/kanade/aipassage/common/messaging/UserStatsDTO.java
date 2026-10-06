package com.kanade.aipassage.common.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户侧统计（user → article，供 /statistics/overview 聚合）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDTO implements Serializable {

    private Long totalUserCount;

    private Long vipUserCount;

    private Long quotaUsed;
}
