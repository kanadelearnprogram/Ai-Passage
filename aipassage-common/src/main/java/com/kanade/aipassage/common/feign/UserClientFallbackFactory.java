package com.kanade.aipassage.common.feign;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.messaging.QuotaRequest;
import com.kanade.aipassage.common.messaging.UserStatsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        log.error("user 服务调用降级, cause={}", cause.toString(), cause);
        return new UserClient() {
            @Override
            public BaseResponse<Boolean> deductQuota(QuotaRequest request) {
                return new BaseResponse<>(42900, null, "系统繁忙，请稍后重试");
            }

            @Override
            public BaseResponse<Boolean> refundQuota(QuotaRequest request) {
                return new BaseResponse<>(50000, null, "配额退还暂不可用（幂等键保留，可事后补偿）");
            }

            @Override
            public BaseResponse<UserStatsDTO> getStats() {
                return new BaseResponse<>(50000, null, "统计服务暂不可用");
            }
        };
    }
}
