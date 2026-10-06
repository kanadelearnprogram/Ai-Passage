package com.kanade.aipassage.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.kanade.aipassage.model.entity.PaymentRecord;
import com.kanade.aipassage.mapper.PaymentRecordMapper;
import com.kanade.aipassage.service.PaymentRecordService;
import org.springframework.stereotype.Service;

/**
 * 支付记录表 服务层实现。
 *
 * @author kanade
 */
@Service
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord>  implements PaymentRecordService{

}
