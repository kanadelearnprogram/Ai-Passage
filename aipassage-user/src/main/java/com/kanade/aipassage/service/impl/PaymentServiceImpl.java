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
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
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
        User user = userMapper.selectOneById(id);

        if (user == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"已经是vip");
        }
        // 检查是否为vip
        if (Objects.equals(user.getUserRole(), UserConstant.VIP_ROLE)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"已经是vip");
        }
        // 创建stripe会话
        ProductTypeEnum productTypeEnum = ProductTypeEnum.VIP_PERMANENT;

        Session session =  createStripeSession(id,productTypeEnum);
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
        // 查询数据库中是否存在记录
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("stripeSessionId",sessionId);
        PaymentRecord record = paymentRecordMapper.selectOneByQuery(queryWrapper);

        if (record == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"没有支付信息");
        }
        // 检查是否处理
        if (record.getStatus().equals(PaymentStatusEnum.SUCCEEDED.getValue())){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"已经支付了");
        }

        // 更新支付状态
        record.setStatus(PaymentStatusEnum.SUCCEEDED.getValue());
        record.setStripePaymentIntentId(paymentIntent);
        paymentRecordMapper.update(record);

        // 更新用户
        User user = new User();
        user.setId(Long.valueOf(userId));
        user.setVipTime(LocalDateTime.now());
        user.setUserRole(UserConstant.VIP_ROLE);
        userMapper.update(user);

    }

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
