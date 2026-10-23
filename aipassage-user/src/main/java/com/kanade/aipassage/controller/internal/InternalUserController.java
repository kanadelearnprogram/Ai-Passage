package com.kanade.aipassage.controller.internal;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.common.messaging.QuotaRequest;
import com.kanade.aipassage.common.messaging.UserStatsDTO;
import com.kanade.aipassage.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务内部接口（仅服务间调用，same-token 防护，不暴露到网关）
 */
@RestController
@RequestMapping("/internal/user")
@Tag(name = "InternalUserController", description = "用户服务内部接口")
public class InternalUserController {

    @Resource
    private QuotaService quotaService;

    /**
     * 扣减创作配额（幂等）
     */
    @PostMapping("/quota/deduct")
    @Operation(summary = "扣减创作配额")
    public BaseResponse<Boolean> deductQuota(@RequestBody QuotaRequest request) {
        return ResultUtils.success(quotaService.deduct(request.getUserId(), request.getTaskId()));
    }

    /**
     * 退还创作配额（幂等）
     */
    @PostMapping("/quota/refund")
    @Operation(summary = "退还创作配额")
    public BaseResponse<Boolean> refundQuota(@RequestBody QuotaRequest request) {
        return ResultUtils.success(quotaService.refund(request.getUserId(), request.getTaskId()));
    }

    /**
     * 用户侧统计
     */
    @GetMapping("/stats")
    @Operation(summary = "用户侧统计")
    public BaseResponse<UserStatsDTO> getStats() {
        return ResultUtils.success(quotaService.getStats());
    }
}
