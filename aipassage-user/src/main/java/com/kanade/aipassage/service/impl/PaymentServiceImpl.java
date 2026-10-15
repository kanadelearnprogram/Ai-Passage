package com.kanade.aipassage.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.kanade.aipassage.config.PaymentConfig;
import com.kanade.aipassage.constant.UserConstant;
import com.kanade.aipassage.exception.BusinessException;
import com.kanade.aipassage.exception.ErrorCode;
import com.kanade.aipassage.mapper.PaymentRecordMapper;
import com.kanade.aipassage.mapper.UserMapper;
import com.kanade.aipassage.model.entity.PaymentRecord;
import com.kanade.aipassage.model.entity.User;
import com.kanade.aipassage.model.enums.PaymentStatusEnum;
import com.kanade.aipassage.model.enums.ProductTypeEnum;
import com.kanade.aipassage.service.PaymentService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCancelParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    @Resource
    UserMapper userMapper;
    @Resource
    PaymentConfig paymentConfig;
    @Resource
    PaymentRecordMapper paymentRecordMapper;

    private static final String CURRENCY_USD = "usd";
    private static final long CENTS_MULTIPLIER = 100L;

    @Override
    public String creatPaymentSession(Long id) throws StripeException {
        log.info("[支付] 开始创建 Checkout 会话, userId={}", id);
        User user = userMapper.selectOneById(id);

        if (user == null){
            log.warn("[支付] 创建会话失败: 用户不存在, userId={}", id);
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"已经是vip");
        }
        // 检查是否为vip
        if (Objects.equals(user.getUserRole(), UserConstant.VIP_ROLE)){
            log.warn("[支付] 创建会话失败: 已是 VIP, userId={}", id);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"已经是vip");
        }
        // 创建stripe会话
        ProductTypeEnum productTypeEnum = ProductTypeEnum.VIP_PERMANENT;

        Session session =  createStripeSession(id,productTypeEnum);
        log.info("[支付] Stripe 会话创建成功, userId={}, sessionId={}, amount={}, url={}",
                id, session.getId(), productTypeEnum.getPrice(), session.getUrl());
        // 保存支付记录
        PaymentRecord record = PaymentRecord.builder()
                .userId(id)
                .stripeSessionId(session.getId())
                .amount(ProductTypeEnum.VIP_PERMANENT.getPrice())
                .currency(CURRENCY_USD)
                .status(PaymentStatusEnum.PENDING.getValue())
                .productType(productTypeEnum.getValue())
                .description(productTypeEnum.getDescription())
                .build();
        paymentRecordMapper.insert(record);
        log.info("[支付] 支付记录已保存(PENDING), userId={}, recordId={}", id, record.getId());
        return session.getUrl();

    }

    @Override
    public Event eventConstruct(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload,sigHeader,paymentConfig.getWebhookSecret());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentSusses(Session session) {
        String sessionId = session.getId();
        String userId = session.getMetadata().get("userId");
        String paymentIntent = session.getPaymentIntent();
        log.info("[支付] 收到支付成功处理请求, userId={}, sessionId={}, paymentIntent={}, paymentStatus={}",
                userId, sessionId, paymentIntent, session.getPaymentStatus());
        // 查询数据库中是否存在记录
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("stripeSessionId",sessionId);
        PaymentRecord record = paymentRecordMapper.selectOneByQuery(queryWrapper);

        if (record == null){
            log.error("[支付] 处理失败: 未找到支付记录, sessionId={}", sessionId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"没有支付信息");
        }
        // 检查是否处理
        if (record.getStatus().equals(PaymentStatusEnum.SUCCEEDED.getValue())){
            log.warn("[支付] 重复事件, 已是 SUCCEEDED, 跳过, sessionId={}, recordId={}", sessionId, record.getId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"已经支付了");
        }

        // 更新支付状态
        record.setStatus(PaymentStatusEnum.SUCCEEDED.getValue());
        record.setStripePaymentIntentId(paymentIntent);
        paymentRecordMapper.update(record);
        log.info("[支付] 支付记录已更新为 SUCCEEDED, recordId={}, userId={}", record.getId(), userId);

        // 更新用户
        User user = new User();
        user.setId(Long.valueOf(userId));
        user.setVipTime(LocalDateTime.now());
        user.setUserRole(UserConstant.VIP_ROLE);
        userMapper.update(user);
        log.info("[支付] 用户已升级为 VIP, userId={}, vipTime={}", userId, user.getVipTime());

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleRefund(Long userId, String reason) throws StripeException {
        log.info("[退款] 开始处理退款, userId={}, reason={}", userId, reason);
        User user = userMapper.selectOneById(userId);

        if (!UserConstant.VIP_ROLE.equals(user.getUserRole())) {
            log.warn("[退款] 处理失败: 非 VIP 用户, userId={}, role={}", userId, user.getUserRole());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您不是会员，无法退款");
        }

        // 检查支付信息
        QueryWrapper queryWrapper = QueryWrapper.create()
        .eq("userId", userId)
        .eq("status", PaymentStatusEnum.SUCCEEDED.getValue())
        .eq("productType", ProductTypeEnum.VIP_PERMANENT.getValue())
        .orderBy("createTime", false)
        .limit(1);
        PaymentRecord paymentRecord = paymentRecordMapper.selectOneByQuery(queryWrapper);;
        if (paymentRecord == null) {
            log.warn("[退款] 处理失败: 未找到 SUCCEEDED 支付记录, userId={}", userId);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到支付记录");
        }

        if (paymentRecord.getStripePaymentIntentId() == null) {
            log.error("[退款] 处理失败: 支付记录缺少 paymentIntent, recordId={}", paymentRecord.getId());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "支付记录无效");
        }
        log.info("[退款] 找到支付记录, recordId={}, paymentIntent={}, amount={} {}",
                paymentRecord.getId(), paymentRecord.getStripePaymentIntentId(),
                paymentRecord.getAmount(), paymentRecord.getCurrency());

        // stripe退款
        RefundCreateParams params = RefundCreateParams
                .builder()
                .setPaymentIntent(paymentRecord.getStripePaymentIntentId())
                .build();
        Refund refund = Refund.create(params);
        log.info("[退款] Stripe 退款已提交, refundId={}, status={}, amount={}",
                refund.getId(), refund.getStatus(), refund.getAmount());

        // 检查退款结果
        if (!"succeeded".equals(refund.getStatus())) {
            log.warn("[退款] Stripe 退款未成功, refundId={}, status={}", refund.getId(), refund.getStatus());
            return false;
        }

        // 更新支付
        PaymentRecord updateRecord = new PaymentRecord();
        updateRecord.setId(paymentRecord.getId());
        updateRecord.setStatus(PaymentStatusEnum.REFUNDED.getValue());
        updateRecord.setRefundTime(LocalDateTime.now());
        updateRecord.setRefundReason(reason);
        paymentRecordMapper.update(updateRecord);
        log.info("[退款] 支付记录已更新为 REFUNDED, recordId={}", paymentRecord.getId());
        //  降级
        UpdateChain.of(User.class)
                .set(User::getUserRole, UserConstant.DEFAULT_ROLE)
                .set(User::getQuota, UserConstant.DEFAULT_QUOTA)
                .set(User::getVipTime, null)
                .where(User::getId).eq(userId)
                .update();
        log.info("[退款] 用户已降级, userId={}, role={}, quota={}",
                userId, UserConstant.DEFAULT_ROLE, UserConstant.DEFAULT_QUOTA);

        log.info("退款成功，已取消 VIP 身份, userId={}, refundId={}", userId, updateRecord.getId());
        return true;
    }

    // todo 退款

    private Session createStripeSession(Long id, ProductTypeEnum productType) throws StripeException {
        long amountInCents = productType.getPrice().multiply(new BigDecimal(CENTS_MULTIPLIER)).longValue();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(paymentConfig.getSuccessUrl())
                .setCancelUrl(paymentConfig.getCancelUrl())
                .addLineItem(buildLineItem(productType, amountInCents))
                .putMetadata("userId", String.valueOf(id))
                .putMetadata("productType", productType.getValue())
                .build();

        return Session.create(params);
    }

    private SessionCreateParams.LineItem buildLineItem(ProductTypeEnum productType, long amountInCents) {
        return SessionCreateParams.LineItem.builder()
                .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(CURRENCY_USD)
                                .setUnitAmount(amountInCents)
                                .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(productType.getDescription())
                                                .setDescription("解锁全部高级功能，无限创作配额，终身有效")
                                                .build()
                                )
                                .build()
                )
                .setQuantity(1L)
                .build();
    }
}
