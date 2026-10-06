package com.kanade.aipassage.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片缓存表 实体类。
 *
 * @author kanade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("image_cache")
public class ImageCache implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * MD5唯一缓存Key
     */
    @Id@Column("cacheKey")
    private String cacheKey;

    /**
     * 图片类型
     */
    @Column("sourceType")
    private String sourceType;

    /**
     * 搜索关键词
     */
    private String keywords;

    /**
     * 提示词/流程图代码
     */
    private String prompt;

    /**
     * COS图片地址
     */
    @Column("cosUrl")
    private String cosUrl;

    /**
     * 0=正常 1=降级图片
     */
    @Column("isFallback")
    private Boolean isFallback;

    /**
     * 缓存命中次数
     */
    @Column("hitCount")
    private Integer hitCount;

}
