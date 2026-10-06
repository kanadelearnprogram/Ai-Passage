package com.kanade.aipassage.common.feign;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.messaging.ImageGenRequest;
import com.kanade.aipassage.common.messaging.ImageGenResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 图片服务内部客户端（ai → image）
 */
@FeignClient(name = "aipassage-image", path = "/api/internal/image")
public interface ImageClient {

    /**
     * 生成/检索图片并转存 COS（服务内部自带两级缓存与降级）
     */
    @PostMapping("/generate")
    BaseResponse<ImageGenResult> generate(@RequestBody ImageGenRequest request);
}
