package com.kanade.aipassage.controller.internal;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.common.messaging.ImageGenRequest;
import com.kanade.aipassage.common.messaging.ImageGenResult;
import com.kanade.aipassage.image.ImageServiceStrategy;
import com.kanade.aipassage.model.dto.ImageRequest;
import com.kanade.aipassage.model.enums.ImageMethodEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片服务内部接口（仅服务间调用，same-token 防护，不暴露到网关）
 */
@RestController
@RequestMapping("/internal/image")
@Tag(name = "InternalImageController", description = "图片服务内部接口")
public class InternalImageController {

    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    /**
     * 生成/检索图片并转存 COS（内部含两级缓存与降级）
     */
    @PostMapping("/generate")
    @Operation(summary = "生成图片")
    public BaseResponse<ImageGenResult> generate(@RequestBody ImageGenRequest genRequest) {
        ImageRequest request = genRequest.getRequest();
        ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                genRequest.getImageSource(), request);
        return ResultUtils.success(new ImageGenResult(
                result.getUrl(),
                result.getMethod() != null ? result.getMethod().getValue() : ImageMethodEnum.getFallbackMethod().getValue()));
    }
}
