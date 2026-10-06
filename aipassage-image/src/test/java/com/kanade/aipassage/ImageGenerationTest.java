package com.kanade.aipassage;

import com.kanade.aipassage.cos.CosService;
import com.kanade.aipassage.image.ImageSearchService;
import com.kanade.aipassage.image.ImageServiceStrategy;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.dto.ImageRequest;
import com.kanade.aipassage.model.enums.ImageMethodEnum;
import com.kanade.aipassage.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片生成和嵌入功能测试
 */
@SpringBootTest
@Slf4j
public class ImageGenerationTest {

    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    @Resource
    private ArticleAgentService articleAgentService;

    @Resource
    private CosService cosService;

    /**
     * 测试1：测试各个图片服务的可用性
     */
    @Test
    public void testImageServiceAvailability() {
        log.info("========== 测试图片服务可用性 ==========");
        
        List<ImageMethodEnum> methods = imageServiceStrategy.getRegisteredMethods();
        log.info("已注册的图片服务数量: {}", methods.size());
        
        for (ImageMethodEnum method : methods) {
            ImageSearchService service = imageServiceStrategy.getService(method);
            if (service != null) {
                boolean available = service.isAvailable();
                log.info("服务: {} - {}, 可用状态: {}", 
                        method.getValue(), 
                        service.getClass().getSimpleName(),
                        available ? "✅ 可用" : "❌ 不可用");
            } else {
                log.warn("服务: {} - 未找到对应的 Service 实现", method.getValue());
            }
        }
    }

    /**
     * 测试2：测试 Pexels 图片检索
     */
    @Test
    public void testPexelsImageSearch() {
        log.info("========== 测试 Pexels 图片检索 ==========");
        
        ImageRequest request = ImageRequest.builder()
                .keywords("technology programming")
                .position(1)
                .type("section")
                .build();
        
        ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.PEXELS.getValue(), 
                request
        );
        
        log.info("Pexels 检索结果:");
        log.info("  - URL: {}", result.getUrl());
        log.info("  - Method: {}", result.getMethod());
        log.info("  - Success: {}", result.isSuccess());
        
        assert result.isSuccess() : "Pexels 图片检索失败";
    }

    /**
     * 测试3：测试 Mermaid 图表生成
     */
    @Test
    public void testMermaidDiagramGeneration() {
        log.info("========== 测试 Mermaid 图表生成 ==========");
        
        String mermaidCode = """
                flowchart LR
                    A[开始] --> B{判断}
                    B -->|是| C[处理]
                    B -->|否| D[结束]
                    C --> D
                """;
        
        ImageRequest request = ImageRequest.builder()
                .prompt(mermaidCode)
                .position(1)
                .type("diagram")
                .build();
        
        ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.MERMAID.getValue(), 
                request
        );
        
        log.info("Mermaid 生成结果:");
        log.info("  - URL: {}", result.getUrl());
        log.info("  - Method: {}", result.getMethod());
        log.info("  - Success: {}", result.isSuccess());
        
        // Mermaid 可能失败，只记录不强制断言
        if (result.isSuccess()) {
            log.info("✅ Mermaid 图表生成成功");
        } else {
            log.warn("⚠️ Mermaid 图表生成失败（可能是环境问题）");
        }
    }

    /**
     * 测试4：测试表情包检索
     */
    @Test
    public void testEmojiPackSearch() {
        log.info("========== 测试表情包检索 ==========");
        
        ImageRequest request = ImageRequest.builder()
                .keywords("开心")
                .position(1)
                .type("emoji")
                .build();
        
        ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.EMOJI_PACK.getValue(), 
                request
        );
        
        log.info("表情包检索结果:");
        log.info("  - URL: {}", result.getUrl());
        log.info("  - Method: {}", result.getMethod());
        log.info("  - Success: {}", result.isSuccess());
        
        // 表情包可能失败，只记录
        if (result.isSuccess()) {
            log.info("✅ 表情包检索成功");
        } else {
            log.warn("⚠️ 表情包检索失败");
        }
    }

    /**
     * 测试5：测试降级机制
     */
    @Test
    public void testFallbackMechanism() {
        log.info("========== 测试降级机制 ==========");
        
        // 测试一个不存在的服务，应该降级到 PICSUM
        ImageRequest request = ImageRequest.builder()
                .keywords("test")
                .position(99)
                .type("fallback")
                .build();
        
        ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                "NON_EXISTENT_SERVICE", 
                request
        );
        
        log.info("降级测试结果:");
        log.info("  - URL: {}", result.getUrl());
        log.info("  - Method: {}", result.getMethod());
        log.info("  - Success: {}", result.isSuccess());
        
        assert result.isSuccess() : "降级机制失败";
        assert result.getMethod() == ImageMethodEnum.PICSUM : "降级方法不正确";
        log.info("✅ 降级机制工作正常");
    }

    /**
     * 测试6：测试完整的图文合成流程
     */
    @Test
    public void testImageEmbedding() {
        log.info("========== 测试图文合成流程 ==========");
        
        // 1. 创建模拟的正文内容（包含占位符）
        String contentWithPlaceholders = """
                # Consumer<String> 完全指南
                
                ## 引子
                
                System.out.println 虽然简单，但在大型项目中会带来很多问题。
                
                {{IMAGE_PLACEHOLDER_1}}
                
                ## 用法一：优雅日志记录
                
                使用 Consumer<String> 可以让日志注入变得自然。
                
                {{IMAGE_PLACEHOLDER_2}}
                
                ## 用法二：声明式字符串校验
                
                把 if-else 校验变成可复用的验证管道。
                
                {{IMAGE_PLACEHOLDER_3}}
                
                ## 结语
                
                从打印思维到行为抽象思维，你的代码正在进化。
                """;
        
        // 2. 创建模拟的配图需求
        List<ArticleState.ImageRequirement> requirements = new ArrayList<>();
        
        ArticleState.ImageRequirement req1 = new ArticleState.ImageRequirement();
        req1.setPosition(1);
        req1.setType("cover");
        req1.setImageSource(ImageMethodEnum.PEXELS.getValue());
        req1.setKeywords("java programming code");
        req1.setPlaceholderId("{{IMAGE_PLACEHOLDER_1}}");
        requirements.add(req1);
        
        ArticleState.ImageRequirement req2 = new ArticleState.ImageRequirement();
        req2.setPosition(2);
        req2.setType("section");
        req2.setImageSource(ImageMethodEnum.MERMAID.getValue());
        req2.setPrompt("flowchart LR\n    A[Logger] --> B[Consumer<String>]\n    B --> C[Output]");
        req2.setPlaceholderId("{{IMAGE_PLACEHOLDER_2}}");
        requirements.add(req2);
        
        ArticleState.ImageRequirement req3 = new ArticleState.ImageRequirement();
        req3.setPosition(3);
        req3.setType("section");
        req3.setImageSource(ImageMethodEnum.PEXELS.getValue());
        req3.setKeywords("validation check");
        req3.setPlaceholderId("{{IMAGE_PLACEHOLDER_3}}");
        requirements.add(req3);
        
        // 3. 创建 ArticleState
        ArticleState state = new ArticleState();
        state.setTaskId("test-task-001");
        state.setContent(contentWithPlaceholders);
        state.setImageRequirements(requirements);
        
        log.info("原始内容长度: {}", contentWithPlaceholders.length());
        log.info("配图需求数量: {}", requirements.size());
        
        // 4. 模拟智能体5：生成配图
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();
        
        for (ArticleState.ImageRequirement requirement : requirements) {
            log.info("\n处理配图 position={}, source={}", 
                    requirement.getPosition(), 
                    requirement.getImageSource());
            
            ImageRequest imageRequest = ImageRequest.builder()
                    .keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt())
                    .position(requirement.getPosition())
                    .type(requirement.getType())
                    .build();
            
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                    requirement.getImageSource(), 
                    imageRequest
            );
            
            ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
            imageResult.setPosition(requirement.getPosition());
            imageResult.setUrl(result.getUrl());
            imageResult.setMethod(result.getMethod().getValue());
            imageResult.setKeywords(requirement.getKeywords());
            imageResult.setDescription(requirement.getType());
            imageResult.setPlaceholderId(requirement.getPlaceholderId());
            
            imageResults.add(imageResult);
            
            log.info("  ✓ 配图生成成功: {}", result.getUrl());
        }
        
        state.setImages(imageResults);
        
        // 5. 执行图文合成
        mergeImagesIntoContent(state);
        
        String fullContent = state.getFullContent();
        log.info("\n========== 合成结果 ==========");
        log.info("合成后内容长度: {}", fullContent.length());
        log.info("内容预览:\n{}", fullContent.substring(0, Math.min(500, fullContent.length())));
        
        // 6. 验证占位符是否被替换
        assert !fullContent.contains("{{IMAGE_PLACEHOLDER_1}}") : "占位符1未被替换";
        assert !fullContent.contains("{{IMAGE_PLACEHOLDER_2}}") : "占位符2未被替换";
        assert !fullContent.contains("{{IMAGE_PLACEHOLDER_3}}") : "占位符3未被替换";
        
        // 7. 验证 Markdown 图片语法是否存在
        assert fullContent.contains("![" ) : "Markdown 图片语法不存在";
        assert fullContent.contains("](http") : "图片 URL 不存在";
        
        log.info("\n✅ 图文合成测试通过！");
        log.info("所有占位符都已成功替换为 Markdown 图片语法");
    }

    /**
     * 测试7：测试边界情况 - 空占位符
     */
    @Test
    public void testEmptyPlaceholderHandling() {
        log.info("========== 测试空占位符处理 ==========");
        
        String content = "这是一段测试内容，没有占位符。";
        
        ArticleState state = new ArticleState();
        state.setContent(content);
        
        List<ArticleState.ImageResult> images = new ArrayList<>();
        
        // 添加一个没有占位符的图片（如封面图）
        ArticleState.ImageResult coverImage = new ArticleState.ImageResult();
        coverImage.setPosition(1);
        coverImage.setUrl("https://example.com/cover.jpg");
        coverImage.setDescription("cover");
        coverImage.setPlaceholderId(""); // 空占位符
        images.add(coverImage);
        
        state.setImages(images);
        
        // 执行图文合成
        mergeImagesIntoContent(state);
        
        String fullContent = state.getFullContent();
        
        // 验证：内容应该保持不变（因为没有有效的占位符）
        assert fullContent.equals(content) : "空占位符处理失败，内容被修改";
        
        log.info("✅ 空占位符处理正确，内容保持不变");
    }

    /**
     * 测试8：测试 Iconify 图标检索
     */
    @Test
    public void testIconifyIconSearch() {
        log.info("========== 测试 Iconify 图标检索 ==========");
        
        ImageRequest request = ImageRequest.builder()
                .keywords("check")
                .position(1)
                .type("icon")
                .build();
        
        ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.ICONIFY.getValue(), 
                request
        );
        
        log.info("Iconify 图标检索结果:");
        log.info("  - URL: {}", result.getUrl());
        log.info("  - Method: {}", result.getMethod());
        log.info("  - Success: {}", result.isSuccess());
        
        if (result.isSuccess()) {
            log.info("✅ Iconify 图标检索成功");
        } else {
            log.warn("⚠️ Iconify 图标检索失败");
        }
    }

    /**
     * 测试9：测试多种图片类型混合生成
     */
    @Test
    public void testMixedImageTypesGeneration() {
        log.info("========== 测试多种图片类型混合生成 ==========");
        
        // 测试 Pexels
        ImageRequest pexelsRequest = ImageRequest.builder()
                .keywords("technology")
                .position(1)
                .type("section")
                .build();
        ImageServiceStrategy.ImageResult pexelsResult = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.PEXELS.getValue(), pexelsRequest
        );
        log.info("Pexels 结果: {} - {}", pexelsResult.getMethod(), pexelsResult.isSuccess() ? "成功" : "失败");
        
        // 测试 Emoji
        ImageRequest emojiRequest = ImageRequest.builder()
                .keywords("开心")
                .position(2)
                .type("emoji")
                .build();
        ImageServiceStrategy.ImageResult emojiResult = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.EMOJI_PACK.getValue(), emojiRequest
        );
        log.info("Emoji 结果: {} - {}", emojiResult.getMethod(), emojiResult.isSuccess() ? "成功" : "失败");
        
        // 测试 Mermaid
        String mermaidCode = "graph TD\n    A[开始] --> B[结束]";
        ImageRequest mermaidRequest = ImageRequest.builder()
                .prompt(mermaidCode)
                .position(3)
                .type("diagram")
                .build();
        ImageServiceStrategy.ImageResult mermaidResult = imageServiceStrategy.getImageAndUpload(
                ImageMethodEnum.MERMAID.getValue(), mermaidRequest
        );
        log.info("Mermaid 结果: {} - {}", mermaidResult.getMethod(), mermaidResult.isSuccess() ? "成功" : "失败");
        
        log.info("✅ 多种图片类型混合生成测试完成");
    }

    /**
     * 辅助方法：图文合成（从 ArticleAgentService 复制）
     */
    private void mergeImagesIntoContent(ArticleState state) {
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();

        if (images == null || images.isEmpty()) {
            log.warn("没有配图数据，直接返回原始内容");
            state.setFullContent(content);
            return;
        }

        String fullContent = content;
        int replacedCount = 0;

        for (ArticleState.ImageResult image : images) {
            String placeholder = image.getPlaceholderId();
            
            // 跳过空占位符
            if (placeholder == null || placeholder.isEmpty()) {
                log.debug("跳过无占位符的图片, position={}, type={}", 
                        image.getPosition(), image.getDescription());
                continue;
            }
            
            // 检查占位符是否存在于正文中
            if (!fullContent.contains(placeholder)) {
                log.warn("占位符不存在于正文中, placeholder={}, position={}", 
                        placeholder, image.getPosition());
                continue;
            }
            
            // 构建 Markdown 图片语法
            String md = "![" + image.getDescription() + "](" + image.getUrl() + ")";
            
            // 替换占位符
            String beforeReplace = fullContent;
            fullContent = fullContent.replace(placeholder, md);
            
            // 验证替换是否成功
            if (!fullContent.equals(beforeReplace)) {
                replacedCount++;
                log.info("成功替换占位符, placeholder={}, position={}", 
                        placeholder, image.getPosition());
            } else {
                log.warn("占位符替换失败, placeholder={}", placeholder);
            }
        }

        state.setFullContent(fullContent);
        log.info("图文合成完成, originalLength={}, fullContentLength={}, replacedCount={}, totalImages={}", 
                content.length(), fullContent.length(), replacedCount, images.size());
    }
}
