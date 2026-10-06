package com.kanade.aipassage.common.messaging;

import com.kanade.aipassage.model.dto.ImageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片生成请求（ai → image）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenRequest implements Serializable {

    /** 图源类型（ImageMethodEnum.value） */
    private String imageSource;

    private ImageRequest request;
}
