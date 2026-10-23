package com.kanade.aipassage.common.feign;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.messaging.QuotaRequest;
import com.kanade.aipassage.common.messaging.UserStatsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户服务内部客户端（article → user）
 */
@FeignClient(name = "aipassage-user", path = "/api/internal/user", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    /**
     * 校验并扣减配额（以 taskId 幂等，重复调用不会重复扣）
     */
    @PostMapping("/quota/deduct")
    BaseResponse<Boolean> deductQuota(@RequestBody QuotaRequest request);

    /**
     * 退还配额（任务失败补偿）
     */
    @PostMapping("/quota/refund")
    BaseResponse<Boolean> refundQuota(@RequestBody QuotaRequest request);

    /**
     * 用户侧统计数据
     */
    @GetMapping("/stats")
    BaseResponse<UserStatsDTO> getStats();
}
