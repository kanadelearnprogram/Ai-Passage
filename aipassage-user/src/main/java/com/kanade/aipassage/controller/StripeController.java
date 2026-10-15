package com.kanade.aipassage.controller;

import com.kanade.aipassage.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@Slf4j
@Hidden
public class StripeController {
    @Resource
    PaymentService paymentService;

    @PostMapping("/stripe")
    public String StripeWebhookHandler(@RequestBody String payload,
                                       @RequestHeader("Stripe-Signature") String sigHeader){
        try {
            Event event = paymentService.eventConstruct(payload,sigHeader);
            switch (event.getType()) {
                case "checkout.session.completed" ->{
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject()
                            .orElseThrow(() -> new RuntimeException("无法解析"));
                    paymentService.handlePaymentSusses(session);
                }
                case "checkout.session.async_payment_succeeded" ->{
                    // 异步支付成功
                    Session asyncSession = (Session) event.getDataObjectDeserializer()
                            .getObject()
                            .orElseThrow(() -> new RuntimeException("无法解析 Session 对象"));
                    paymentService.handlePaymentSusses(asyncSession);
                }
                default -> {
                    log.info("未处理的事件类型: {}", event.getType());
                }
            }

            return "success";
        } catch (SignatureVerificationException e) {
            log.error("处理 Stripe Webhook 失败", e);
            return "error";
        }
    }
}
