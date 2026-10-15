package com.kanade.aipassage.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.constant.UserConstant;
import com.kanade.aipassage.exception.BusinessException;
import com.kanade.aipassage.exception.ErrorCode;
import com.kanade.aipassage.model.entity.User;
import com.kanade.aipassage.service.PaymentService;
import com.kanade.aipassage.service.UserService;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
@Slf4j
public class PaymentController {
    @Resource
    private UserService userService;

    @Resource
    private PaymentService paymentService;

    // 创建支付
    @PostMapping("/create")
    public BaseResponse<String> createPayment() {
        Long id = StpUtil.getLoginIdAsLong();
        log.info("[支付] 收到创建支付请求, userId={}", id);
        //支付
        try {
            String url = paymentService.creatPaymentSession(id);
            log.info("[支付] 创建支付成功, userId={}, 返回 Stripe 收银台 URL", id);
            return ResultUtils.success(url);
        } catch (StripeException e) {
            log.error("[支付] 创建 Stripe 会话异常, userId={}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"支付失败");
        }
    }

    @PostMapping("/refund")
    @Operation(summary = "申请退款")
    @SaCheckRole( UserConstant.VIP_ROLE)
    public BaseResponse<Boolean> refund(
            @RequestParam(required = false) String reason) {
        User loginUser = userService.getLoginUser();
        log.info("[退款] 收到退款请求, userId={}, reason={}", loginUser.getId(), reason);
        try {
            boolean success = paymentService.handleRefund(loginUser.getId(), reason);
            log.info("[退款] 退款请求处理结束, userId={}, success={}", loginUser.getId(), success);
            return ResultUtils.success(success);
        } catch (BusinessException e) {
            log.warn("[退款] 业务异常, userId={}, code={}, msg={}", loginUser.getId(), e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("退款失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "退款失败");
        }
    }
}
