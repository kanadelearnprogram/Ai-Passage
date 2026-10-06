package com.kanade.aipassage.common.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.kanade.aipassage.constant.UserConstant;

/**
 * 登录态读取工具
 * 登录时 user 服务把角色写入 Sa-Token 账号会话（Redis 共享），其余服务直接读取。
 */
public class LoginUserHolder {

    public static long getLoginId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static String getRole() {
        Object role = StpUtil.getSession().get(UserConstant.ROLE_SESSION_KEY);
        return role == null ? null : role.toString();
    }

    public static LoginUser getLoginUser() {
        return new LoginUser(getLoginId(), getRole());
    }

    public static boolean isAdmin() {
        return UserConstant.ADMIN_ROLE.equals(getRole());
    }
}
