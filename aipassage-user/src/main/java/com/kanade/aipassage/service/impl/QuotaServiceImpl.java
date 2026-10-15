package com.kanade.aipassage.service.impl;

import com.kanade.aipassage.common.messaging.MessagingConstant;
import com.kanade.aipassage.common.messaging.UserStatsDTO;
import com.kanade.aipassage.constant.UserConstant;
import com.kanade.aipassage.exception.BusinessException;
import com.kanade.aipassage.exception.ErrorCode;
import com.kanade.aipassage.model.entity.User;
import com.kanade.aipassage.mapper.UserMapper;
import com.kanade.aipassage.service.QuotaService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    /** 幂等标记有效期：超过 7 天的任务不允许再补偿 */
    private static final long MARKER_TTL_DAYS = 7;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean deduct(Long userId, String taskId) {
        String marker = MessagingConstant.QUOTA_TASK_PREFIX + taskId;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(marker, "1", MARKER_TTL_DAYS, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(first)) {
            // 同一任务已扣过，幂等放行
            log.info("配额已扣减过, 幂等放行, userId={}, taskId={}", userId, taskId);
            return true;
        }
        User user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if(user.getUserRole().equals(UserConstant.VIP_ROLE)){
            return true;
        }
        int quota = user.getQuota() == null ? UserConstant.DEFAULT_QUOTA : user.getQuota();
        if (quota <= 0) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "创作配额不足，请充值或升级 VIP");
        }
        // 条件更新保证并发下不超扣
        boolean updated = UpdateChain.of(User.class)
                .setRaw(User::getQuota, "quota - 1")
                .where(User::getId).eq(userId)
                .and(User::getQuota).gt(0)
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "创作配额不足，请充值或升级 VIP");
        }
        log.info("配额扣减成功, userId={}, taskId={}, 剩余={}", userId, taskId, quota - 1);
        return true;
    }

    @Override
    public boolean refund(Long userId, String taskId) {
        String marker = MessagingConstant.QUOTA_TASK_PREFIX + taskId;
        Object marked = redisTemplate.opsForValue().get(marker);
        if (marked == null) {
            // 从未扣减过（或标记已过期），无需退还
            log.info("配额无需退还（无扣减记录）, userId={}, taskId={}", userId, taskId);
            return true;
        }
        UpdateChain.of(User.class)
                .setRaw(User::getQuota, "quota + 1")
                .where(User::getId).eq(userId)
                .update();
        redisTemplate.delete(marker);
        log.info("配额退还成功, userId={}, taskId={}", userId, taskId);
        return true;
    }

    @Override
    public UserStatsDTO getStats() {
        Long totalUserCount = userMapper.selectCountByQuery(QueryWrapper.create());
        Long vipUserCount = userMapper.selectCountByQuery(
                QueryWrapper.create().eq("userRole", UserConstant.VIP_ROLE));
        var normalUsers = userMapper.selectListByQuery(
                QueryWrapper.create().eq("userRole", UserConstant.DEFAULT_ROLE));
        long normalCount = normalUsers.size();
        long remaining = normalUsers.stream()
                .mapToInt(user -> user.getQuota() != null ? user.getQuota() : 0)
                .sum();
        long quotaUsed = normalCount * UserConstant.DEFAULT_QUOTA - remaining;
        return new UserStatsDTO(totalUserCount, vipUserCount, quotaUsed);
    }
    // todo 配额
}
