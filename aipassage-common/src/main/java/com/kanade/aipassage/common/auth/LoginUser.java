package com.kanade.aipassage.common.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户视图（跨服务通用，仅含权限判断所需字段）
 */
@Data
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long id;

    private String userRole;

    public boolean isAdmin() {
        return "admin".equals(userRole);
    }
}
