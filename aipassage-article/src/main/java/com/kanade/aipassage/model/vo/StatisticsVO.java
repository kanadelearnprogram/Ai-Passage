package com.kanade.aipassage.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsVO implements Serializable {

    private static final long serialVersionId = 1L;

    private Long todayCount;

    private Long weekCount;

    private Long monthCount;

    private Long totalCount;

    private Double successCount;

    private Integer avgDurationMs;

    private Long activeUserCount;

    private Long totalUserCount;

    private Long vipUserCount;

    private Long quotaUsed;
    private Double successRate;
}
