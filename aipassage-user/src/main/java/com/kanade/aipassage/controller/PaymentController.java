package com.kanade.aipassage.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.service.PaymentService;
import com.kanade.aipassage.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        String id = StpUtil.getSession().getId();
        //支付
        String url = paymentService.creatPaymentSession(id);
        return ResultUtils.success(url);
    }
}
