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
            log.info("[Webhook] 收到 Stripe 事件, eventId={}, type={}", event.getId(), event.getType());
            switch (event.getType()) {
                case "checkout.session.completed" ->{
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject()
                            .orElseThrow(() -> new RuntimeException("无法解析"));
                    log.info("[Webhook] 事件 checkout.session.completed, sessionId={}, userId={}, paymentIntent={}",
                            session.getId(),
                            session.getMetadata() != null ? session.getMetadata().get("userId") : null,
                            session.getPaymentIntent());
                    paymentService.handlePaymentSusses(session);
                    log.info("[Webhook] checkout.session.completed 处理完成, sessionId={}", session.getId());
                }
                case "checkout.session.async_payment_succeeded" ->{
                    // 异步支付成功
                    Session asyncSession = (Session) event.getDataObjectDeserializer()
                            .getObject()
                            .orElseThrow(() -> new RuntimeException("无法解析 Session 对象"));
                    log.info("[Webhook] 事件 checkout.session.async_payment_succeeded, sessionId={}, userId={}",
                            asyncSession.getId(),
                            asyncSession.getMetadata() != null ? asyncSession.getMetadata().get("userId") : null);
                    paymentService.handlePaymentSusses(asyncSession);
                    log.info("[Webhook] checkout.session.async_payment_succeeded 处理完成, sessionId={}", asyncSession.getId());
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
