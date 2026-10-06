package com.kanade.aipassage.constant;

public interface UserConstant {
    /**
     * 用户登录态键（旧 Session 方案遗留，仅作兼容保留）
     */
    String USER_LOGIN_STATE = "user_login";

    /**
     * Sa-Token 账号会话中存放角色的键
     */
    String ROLE_SESSION_KEY = "aipassage:role";

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";


    int DEFAULT_QUOTA = 5;
    String VIP_ROLE = "vip";


}

