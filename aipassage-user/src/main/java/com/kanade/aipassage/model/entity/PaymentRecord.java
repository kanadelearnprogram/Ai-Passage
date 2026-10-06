package com.kanade.aipassage.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付记录表 实体类。
 *
 * @author kanade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_record")
public class PaymentRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * Stripe Checkout Session ID
     */
    @Column("stripeSessionId")
    private String stripeSessionId;

    /**
     * Stripe 支付意向ID
     */
    @Column("stripePaymentIntentId")
    private String stripePaymentIntentId;

    /**
     * 金额（美元）
     */
    private BigDecimal amount;

    /**
     * 货币
     */
    private String currency;

    /**
     * 状态：PENDING/SUCCEEDED/FAILED/REFUNDED
     */
    private String status;

    /**
     * 产品类型：VIP_PERMANENT
     */
    @Column("productType")
    private String productType;

    /**
     * 描述
     */
    private String description;

    /**
     * 退款时间
     */
    @Column("refundTime")
    private LocalDateTime refundTime;

    /**
     * 退款原因
     */
    @Column("refundReason")
    private String refundReason;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

}
