package com.kanade.aipassage.agent.tool;

import com.kanade.aipassage.common.feign.FeignResponseUtils;
import com.kanade.aipassage.common.feign.ImageClient;
import com.kanade.aipassage.common.messaging.ImageGenRequest;
import com.kanade.aipassage.common.messaging.ImageGenResult;
import com.kanade.aipassage.model.dto.ImageRequest;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 图片生成工具：经 Feign 调用 image 服务（策略选择、两级缓存、COS 转存均在 image 服务内）
 */
@Component
@Slf4j
public class ImageGenerationTool {

    @Resource
    private ImageClient imageClient;

    /**
     * 根据需求生成或搜索图片
     */
    @Tool(description = "根据需求生成或搜索图片。支持多种图片来源")
    public String generateImage(
            @ToolParam(description = "图片来源类型") String imageSource,
            @ToolParam(description = "搜索关键词") String keywords,
            @ToolParam(description = "AI 生图提示词或图表代码") String prompt,
            @ToolParam(description = "图片位置序号") Integer position,
            @ToolParam(description = "图片类型：cover 或 section") String type,
            @ToolParam(description = "对应的章节标题") String sectionTitle) {

        log.info("ImageGenerationTool 开始执行: imageSource={}, position={}", imageSource, position);
        ImageGenerationResult result = generateImageDirect(imageSource, keywords, prompt, position, type, sectionTitle, null);
        return GsonUtils.toJson(result);
    }

    // todo 让ai检查图片是否符合要求 不符合重新生成搜索

    /**
     * 直接生成图片（不通过 Agent，供内部调用）
     */
    public ImageGenerationResult generateImageDirect(String imageSource, String keywords, String prompt,
                                                     Integer position, String type, String sectionTitle,
                                                     String placeholderId) {
        return generateImageDirect(imageSource, keywords, prompt, position, type, sectionTitle, placeholderId, null);
    }

    /**
     * 直接生成图片（支持文章标题上下文）
     */
    public ImageGenerationResult generateImageDirect(String imageSource, String keywords, String prompt,
                                                     Integer position, String type, String sectionTitle,
                                                     String placeholderId, String articleTitle) {
        try {
            ImageRequest imageRequest = ImageRequest.builder()
                    .keywords(keywords)
                    .prompt(prompt)
                    .position(position)
                    .type(type)
                    // 添加上下文信息用于生成细粒度缓存键
                    .articleTitle(articleTitle)
                    .sectionTitle(sectionTitle)
                    .build();

            ImageGenResult genResult = FeignResponseUtils.unwrap(
                    imageClient.generate(new ImageGenRequest(imageSource, imageRequest)));

            ImageGenerationResult generationResult = new ImageGenerationResult();
            generationResult.setPosition(position);
            generationResult.setUrl(genResult.getUrl());
            generationResult.setMethod(genResult.getMethod());
            generationResult.setKeywords(keywords);
            generationResult.setSectionTitle(sectionTitle);
            generationResult.setDescription(type);
            generationResult.setPlaceholderId(placeholderId);
            generationResult.setSuccess(true);

            return generationResult;

        } catch (Exception e) {
            log.error("图片生成失败: imageSource={}, position={}", imageSource, position, e);

            ImageGenerationResult failResult = new ImageGenerationResult();
            failResult.setPosition(position);
            failResult.setSuccess(false);
            failResult.setError(e.getMessage());
            failResult.setSectionTitle(sectionTitle);
            failResult.setPlaceholderId(placeholderId);

            return failResult;
        }
    }

    /**
     * 图片生成结果
     */
    @Data
    public static class ImageGenerationResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer position;
        private String url;
        private String method;
        private String keywords;
        private String sectionTitle;
        private String description;
        private String placeholderId;
        private boolean success;
        private String error;
    }
}
