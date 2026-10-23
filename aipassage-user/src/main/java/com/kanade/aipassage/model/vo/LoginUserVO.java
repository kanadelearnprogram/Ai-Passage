package com.kanade.aipassage.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LoginUserVO implements Serializable {
    private Long id;
    private String userAccount;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;

    /**
     * 剩余创作配额（前端创作页展示与禁用判断使用）
     */
    private Integer quota;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

