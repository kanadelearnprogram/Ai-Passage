package com.kanade.aipassage.common.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片生成结果（image → ai）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenResult implements Serializable {

    private String url;

    /** ImageMethodEnum.value */
    private String method;
}
