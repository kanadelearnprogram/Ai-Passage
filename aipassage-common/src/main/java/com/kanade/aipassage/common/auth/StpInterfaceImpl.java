package com.kanade.aipassage.common.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.kanade.aipassage.constant.UserConstant;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 注解鉴权（@SaCheckRole / @SaCheckPermission）的角色来源。
 * 登录时 user 服务把角色写入账号会话（Redis 共享），各服务从这里读出。
 * 不实现该类时注解拿到的角色永远是空列表，任何账号都会抛 NotRoleException。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return Collections.emptyList();
        }
        Object role = session.get(UserConstant.ROLE_SESSION_KEY);
        return role == null ? Collections.emptyList() : Collections.singletonList(role.toString());
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 目前只按角色鉴权，没有独立权限点
        return Collections.emptyList();
    }
}
