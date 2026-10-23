package com.kanade.aipassage.image;

import cn.hutool.core.util.StrUtil;
import com.kanade.aipassage.model.dto.ImageData;
import com.kanade.aipassage.model.dto.ImageRequest;
import com.kanade.aipassage.model.enums.ImageMethodEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import static com.kanade.aipassage.constant.ArticleConstant.PICSUM_URL_TEMPLATE;

/**
 * SVG 概念示意图生成服务
 * 使用 DeepSeek 生成 SVG 代码（概念示意、思维导图样式、逻辑关系展示等场景），
 * 生成的 SVG 字节数据由 ImageServiceStrategy 统一上传 COS。
 */
@Service
@Slf4j
public class SvgDiagramService implements ImageSearchService {

    private final DeepSeekChatModel chatModel;

    public SvgDiagramService(DeepSeekChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * DeepSeek API Key 为空时该服务不可用，由 Strategy 自动降级 PICSUM
     */
    @Value("${spring.ai.deepseek.api-key:}")
    private String apiKey;

    private static final String SVG_DIAGRAM_PROMPT = """
            ### 背景 ###
            你是一位资深的信息可视化设计师，擅长将抽象概念转化为直观易懂的 SVG 示意图。
            你的作品曾用于知名媒体和技术文档，风格简洁现代、逻辑清晰。

            ### 需求 ###
            {requirement}

            ### 任务步骤 ###
            1. 分析需求：理解要表达的核心概念和逻辑关系
            2. 设计布局：确定图形的整体结构（中心辐射、层级、流程等）
            3. 选择元素：使用圆形、矩形、箭头、连线等基础图形
            4. 配色美化：应用现代配色方案，确保视觉协调
            5. 生成代码：输出完整规范的 SVG 代码

            ### 技术规范 ###
            - 必须包含 <?xml version="1.0" encoding="UTF-8"?> 声明
            - 必须设置 viewBox="0 0 800 600"，便于自适应缩放
            - 字体使用 font-family="Arial, sans-serif"，确保跨平台兼容
            - 使用语义化的 id 和 class 命名

            ### 设计风格 ###
            - 配色：蓝色系为主（#4A90D9、#6BB3F0、#E8F4FC），辅以渐变效果
            - 布局：留白充足，元素间距均匀，层次分明
            - 文字：标签简洁，字号适中（14-18px），颜色对比清晰
            - 连线：使用带箭头的线条表示方向和关系，线条粗细 2-3px

            ### 输出要求 ###
            直接返回完整的 SVG XML 代码，不要有任何解释或其他内容。
            """;

    @Override
    public String searchImage(String keywords) {
        // 此方法已废弃，请使用 getImageData()
        // 返回 null，上传逻辑由 ImageServiceStrategy 统一处理
        return null;
    }

    @Override
    public ImageData getImageData(ImageRequest request) {
        // AI 生图方式：优先使用 prompt，否则使用 keywords
        String requirement = request.getEffectiveParam(true);
        return generateSvgDiagramData(requirement);
    }

    /**
     * 生成 SVG 概念示意图数据
     *
     * @param requirement 示意图需求描述
     * @return ImageData 包含 SVG 字节数据，生成失败返回 null
     */
    public ImageData generateSvgDiagramData(String requirement) {
        if (StrUtil.isBlank(requirement)) {
            log.warn("SVG 图表需求为空");
            return null;
        }
        try {
            // 1. 调用 LLM 生成 SVG 代码
            String svgCode = callLlmToGenerateSvg(requirement);
            if (StrUtil.isBlank(svgCode)) {
                log.error("LLM 未生成 SVG 代码");
                return null;
            }

            // 2. 验证 SVG 格式
            if (!isValidSvg(svgCode)) {
                log.error("生成的 SVG 代码格式无效");
                return null;
            }

            // 3. 转换为字节数据，由 Strategy 统一上传 COS
            byte[] svgBytes = svgCode.getBytes(StandardCharsets.UTF_8);
            log.info("SVG 概念示意图生成成功, size={} bytes", svgBytes.length);
            return ImageData.fromBytes(svgBytes, "image/svg+xml");
        } catch (Exception e) {
            log.error("SVG 概念示意图生成异常, requirement={}", requirement, e);
            return null;
        }
    }

    /**
     * 调用 LLM 生成 SVG 代码
     */
    private String callLlmToGenerateSvg(String requirement) {
        String prompt = SVG_DIAGRAM_PROMPT.replace("{requirement}", requirement);
        log.info("开始调用 LLM 生成 SVG 概念示意图");

        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        String svgCode = response.getResult().getOutput().getText().trim();

        // 提取 SVG 代码（移除可能的 markdown 代码块标记）
        return extractSvgCode(svgCode);
    }

    /**
     * 提取 SVG 代码（去除 markdown 代码块，补齐 XML 声明）
     */
    private String extractSvgCode(String text) {
        if (text == null) {
            return null;
        }
        text = text.replace("```xml", "").replace("```svg", "").replace("```", "").trim();

        // 确保包含 XML 声明
        if (!text.startsWith("<?xml") && text.contains("<svg")) {
            text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + text;
        }
        return text;
    }

    /**
     * 验证 SVG 格式（基本验证：包含 svg 标签）
     */
    private boolean isValidSvg(String svgCode) {
        if (StrUtil.isBlank(svgCode)) {
            return false;
        }
        return svgCode.contains("<svg") && svgCode.contains("</svg>");
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.SVG_DIAGRAM;
    }

    @Override
    public String getFallbackImage(int position) {
        return String.format(PICSUM_URL_TEMPLATE, position);
    }

    @Override
    public boolean isAvailable() {
        return StrUtil.isNotBlank(apiKey);
    }
}
