package com.kanade.aipassage.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.kanade.aipassage.annotation.AgentExecution;
import com.kanade.aipassage.constant.PromptConstant;
import com.kanade.aipassage.cos.CosService;
import com.kanade.aipassage.image.ImageServiceStrategy;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.dto.ImageRequest;
import com.kanade.aipassage.model.enums.ArticleStyleEnum;
import com.kanade.aipassage.model.enums.ImageMethodEnum;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class ArticleAgentService {
    @Resource
    private DashScopeChatModel chatModel;

//    @Resource
//    private ImageSearchService imageSearchService;

    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    @Resource
    private CosService cosService;

    // 执行agent
//    public void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
//        try {
//            // 智能体1：生成标题
//            log.info("智能体1：开始生成标题, taskId={}", state.getTaskId());
//            executePhase1_GenerateTitles(state);
//            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
//
//            // 智能体2：生成大纲（流式输出）
//            log.info("智能体2：开始生成大纲, taskId={}", state.getTaskId());
//            agent2GenerateOutline(state, streamHandler);
//            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
//
//            // 智能体3：生成正文（流式输出）
//            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
//            agent3GenerateContent(state, streamHandler);
//            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
//
//            // 智能体4：分析配图需求
//            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
//            agent4AnalyzeImageRequirements(state);
//            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
//
//            // 智能体5：生成配图
//            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
//            agent5GenerateImages(state, streamHandler);
//            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
//
//            // 图文合成：将配图插入正文
//            log.info("开始图文合成, taskId={}", state.getTaskId());
//            mergeImagesIntoContent(state);
//            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
//
//            log.info("文章生成完成, taskId={}", state.getTaskId());
//        } catch (Exception e) {
//            log.error("文章生成失败, taskId={}", state.getTaskId(), e);
//            throw new RuntimeException("文章生成失败: " + e.getMessage(), e);
//        }
//    }

    private void agent1GenerateTitle(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT.replace("{topic}",state.getTopic())
                + getStylePrompt(state.getStyle());
        // call llm
        String content = callLlm(prompt);

        // 处理json
        ArticleState.TitleResult titleResult = parseJsonResponse(content, ArticleState.TitleResult.class,"标题");
        state.setTitle(titleResult);
        log.info("智能体1：标题生成成功, mainTitle={}", titleResult.getMainTitle());

    }


    /**
     * 智能体2：生成大纲（流式输出）
     */
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        // 构建 prompt，根据是否有用户补充描述插入对应部分
        String descriptionSection = "";
        if (state.getUserDescription() != null && !state.getUserDescription().trim().isEmpty()) {
            descriptionSection = PromptConstant.AGENT2_DESCRIPTION_SECTION
                    .replace("{userDescription}", state.getUserDescription());
        }

        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{descriptionSection}", descriptionSection)
                + getStylePrompt(state.getStyle());

        String content = callLlmWithStream(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：大纲生成成功, sections={}", outlineResult.getSections().size());
    }

    private void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText) + getStylePrompt(state.getStyle());

        String content = callLlmWithStream(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：正文生成成功, length={}", content.length());
    }

    /**
     * 智能体4：分析配图需求
     */
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent());

        String content = callLlm(prompt);
        
        // 先解析外层对象
        try {
            com.google.gson.JsonObject jsonObject = GsonUtils.getInstance().fromJson(content, com.google.gson.JsonObject.class);
            
            // 提取 imageRequirements 数组
            if (jsonObject.has("imageRequirements")) {
                List<ArticleState.ImageRequirement> imageRequirements = GsonUtils.fromJson(
                        jsonObject.get("imageRequirements").toString(),
                        new TypeToken<List<ArticleState.ImageRequirement>>(){}
                );
                state.setImageRequirements(imageRequirements);
                log.info("智能体4：配图需求分析成功, count={}", imageRequirements.size());
            } else {
                throw new RuntimeException("LLM 响应中缺少 imageRequirements 字段");
            }
            
            // 如果还需要保存带占位符的内容，可以提取 contentWithPlaceholders
            if (jsonObject.has("contentWithPlaceholders")) {
                String contentWithPlaceholders = jsonObject.get("contentWithPlaceholders").getAsString();
                // 关键：更新 state 的内容为带占位符的版本，这样后续才能正确替换
                state.setContent(contentWithPlaceholders);
                log.info("获取到带占位符的内容, length={}, placeholders will be replaced later", contentWithPlaceholders.length());
            } else {
                log.warn("LLM 响应中缺少 contentWithPlaceholders 字段，将无法插入图片");
            }
        } catch (Exception e) {
            log.error("配图需求解析失败, content={}", content, e);
            throw new RuntimeException("配图需求解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体5：生成配图（串行执行）
     */
    private void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            String imageSource = requirement.getImageSource();
            log.info("智能体5：开始获取配图, position={}, imageSource={}, keywords={}",
                    requirement.getPosition(), imageSource, requirement.getKeywords());

            // 构建图片请求对象
            ImageRequest imageRequest = ImageRequest.builder()
                    .keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt())
                    .position(requirement.getPosition())
                    .type(requirement.getType())
                    // 添加上下文信息用于生成细粒度缓存键
                    .articleTitle(state.getTitle() != null ? state.getTitle().getMainTitle() : null)
                    .sectionTitle(requirement.getSectionTitle())
                    .build();

            // 使用策略模式获取图片并统一上传到 COS
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);

            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            // 创建配图结果
            ArticleState.ImageResult imageResult = buildImageResult(requirement, cosUrl, method);
            imageResults.add(imageResult);

            // 推送单张配图完成
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：配图获取并上传成功, position={}, method={}, cosUrl={}",
                    requirement.getPosition(), method.getValue(), cosUrl);
        }

        state.setImages(imageResults);
        log.info("智能体5：所有配图生成并上传完成, count={}", imageResults.size());
    }


    /**
     * 图文合成：将配图插入正文对应位置
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
            
            // 跳过空占位符（如封面图可能没有占位符）
            if (placeholder == null || placeholder.isEmpty()) {
                log.debug("跳过无占位符的图片, position={}, type={}", image.getPosition(), image.getDescription());
                continue;
            }
            
            // 检查占位符是否存在于正文中
            if (!fullContent.contains(placeholder)) {
                log.warn("占位符不存在于正文中, placeholder={}, position={}", placeholder, image.getPosition());
                continue;
            }
            
            // 构建 Markdown 图片语法
            String md = "![" + image.getDescription() +"]("+image.getUrl() +")";
            
            // 替换占位符
            String beforeReplace = fullContent;
            fullContent = fullContent.replace(placeholder, md);
            
            // 验证替换是否成功
            if (!fullContent.equals(beforeReplace)) {
                replacedCount++;
                log.info("成功替换占位符, placeholder={}, position={}, url={}", 
                        placeholder, image.getPosition(), image.getUrl());
            } else {
                log.warn("占位符替换失败, placeholder={}", placeholder);
            }
        }

        state.setFullContent(fullContent);
        log.info("图文合成完成, originalLength={}, fullContentLength={}, replacedCount={}, totalImages={}", 
                content.length(), fullContent.length(), replacedCount, images.size());
    }
    /**
     * 阶段1：生成标题方案（3-5个）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    @AgentExecution(value = "agent1_generate_titles", description = "生成标题方案")
    public void executePhase1_GenerateTitles(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题方案
            log.info("阶段1：开始生成标题方案, taskId={}", state.getTaskId());
            agent1GenerateTitleOptions(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            log.info("阶段1：标题方案生成完成, taskId={}, optionsCount={}",
                    state.getTaskId(), state.getTitleOptions().size());
        } catch (Exception e) {
            log.error("阶段1：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }
    /**
     * 智能体1：生成标题方案（3-5个）
     */
    private void agent1GenerateTitleOptions(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", state.getTopic())
                + getStylePrompt(state.getStyle());

        String content = callLlm(prompt);
        List<ArticleState.TitleOption> titleOptions = parseJsonListResponse(
                content,
                new TypeToken<List<ArticleState.TitleOption>>(){},
                "标题方案"
        );
        state.setTitleOptions(titleOptions);
        log.info("智能体1：标题方案生成成功, optionsCount={}", titleOptions.size());
    }


    /**
     * 阶段2：生成大纲（用户选择标题后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    @AgentExecution(value = "agent2_generate_outline", description = "生成文章大纲")
    public void executePhase2_GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体2：生成大纲（流式输出）
            log.info("阶段2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            log.info("阶段2：大纲生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段2：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段3：生成正文+配图（用户确认大纲后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    @AgentExecution(value = "agent3_generate_content", description = "生成文章正文")
    public void executePhase3_GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体3：生成正文（流式输出）
            log.info("阶段3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("阶段3：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("阶段3：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("阶段3：开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("阶段3：正文生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段3：正文生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文生成失败: " + e.getMessage(), e);
        }
    }


    private String callLlm(String prompt){
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    private String callLlmWithStream(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum sseMessageTypeEnum ){
        // stream  推送前端
        // stringBuilder 拼接后保存

        StringBuilder stringBuilder = new StringBuilder();

        Flux<ChatResponse> streamRes = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamRes.doOnNext(chatResponse -> {
            String chunk = chatResponse.getResult().getOutput().getText();
            if (!chunk.isEmpty()){
                stringBuilder.append(chunk);
                streamHandler.accept(sseMessageTypeEnum.getStreamingPrefix() + chunk);
            }
        })
                .doOnError(error -> log.error("llm stream wrong messageType={}",sseMessageTypeEnum,error))
                .blockLast();
        return stringBuilder.toString();
    }

    //解析 JSON 响应
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    // 解析 JSON 列表响应
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    // 配图
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement,
                                                      String imageUrl,
                                                      ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        imageResult.setPlaceholderId(requirement.getPlaceholderId());
        return imageResult;
    }

    private void insertImageAfterSection(StringBuilder fullContent,
                                         List<ArticleState.ImageResult> images,
                                         String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 &&
                    image.getSectionTitle() != null &&
                    sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![").append(image.getDescription())
                        .append("](").append(image.getUrl()).append(")\n");
                break;
            }
        }
    }
    private String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }

        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            return "";
        }

        return switch (styleEnum) {
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }

    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle, String subTitle, List<ArticleState.OutlineSection> current, String modifySuggestion) {

        String currentOutlineJson = GsonUtils.toJson(current);

        String prompt = PromptConstant.AI_MODIFY_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{currentOutline}", currentOutlineJson)
                .replace("{modifySuggestion}", modifySuggestion);

        String content = callLlm(prompt);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "修改后的大纲");

        log.info("AI修改大纲成功, sectionsCount={}", outlineResult.getSections().size());
        return outlineResult.getSections();


    }

    private ArticleAgentService getProxy() {
        try {
            return (ArticleAgentService) AopContext.currentProxy();
        } catch (IllegalStateException e) {
            // 如果获取代理失败，返回 this（降级处理）
            log.warn("获取 AOP 代理对象失败，使用原始对象: {}", e.getMessage());
            return this;
        }
    }
}
