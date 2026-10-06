package com.kanade.aipassage.common.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 配额扣减/退还请求（article → user，以 taskId 幂等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaRequest implements Serializable {

    private Long userId;

    private String taskId;
}
