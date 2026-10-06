package com.kanade.aipassage.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.kanade.aipassage.agent.context.StreamHandlerContext;
import com.kanade.aipassage.agent.tool.ImageGenerationTool;
import com.kanade.aipassage.constant.PromptConstant;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.enums.ArticleStyleEnum;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.IntStream;


/**
 * 正文生成 Agent
 * 根据大纲生成文章正文内容（支持流式输出）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentGeneratorAgent implements NodeAction {

    private final DashScopeChatModel chatModel;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_OUTLINE = "outline";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_CONTENT = "content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));

        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");

        @SuppressWarnings("unchecked")
        ArticleState.OutlineResult outline = state.value(INPUT_OUTLINE)
                .map(v -> {
                    if (v instanceof ArticleState.OutlineResult) {
                        return (ArticleState.OutlineResult) v;
                    }
                    return GsonUtils.fromJson(GsonUtils.toJson(v), ArticleState.OutlineResult.class);
                })
                .orElseThrow(() -> new IllegalArgumentException("缺少大纲参数"));

        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);

        log.info("ContentGeneratorAgent 开始执行: mainTitle={}", mainTitle);

        // 构建 prompt
        String outlineText = GsonUtils.toJson(outline.getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{outline}", outlineText)
                + getStylePrompt(style);

        log.info("outline {}",outline);
        
        // 获取streamHandler(在主线程中)
        Consumer<String> streamHandler = StreamHandlerContext.get();
        
        // 使用ConcurrentHashMap保证线程安全,Key为章节索引,Value为章节内容
        ConcurrentHashMap<Integer, String> sectionContents = new ConcurrentHashMap<>();
        
        // 并行生成每个章节的内容
        List<CompletableFuture<Void>> futures = IntStream.range(0, outline.getSections().size())
                .mapToObj(index -> {
                    ArticleState.OutlineSection section = outline.getSections().get(index);
                    return CompletableFuture.runAsync(() -> {
                        try {
                            // 为每个章节构建独立的prompt
                            String sectionPrompt = prompt.replace("{finish}", GsonUtils.toJson(section));
                            
                            // 调用LLM生成内容(流式输出) - 复用原有方法
                            String sectionContent = callLlmWithStreaming(sectionPrompt, streamHandler,index);
                            
                            // 保存章节内容到Map
                            sectionContents.put(index, sectionContent);
                            log.info("章节{}生成完成,长度={}", index, sectionContent.length());
                            
                            // 推送章节完成信号
                            if (streamHandler != null) {
                                try {
                                    Map<String, Object> completeMsg = new java.util.HashMap<>();
                                    completeMsg.put("type", "CHAPTER_COMPLETE");
                                    completeMsg.put("chapterIndex", index);
                                    completeMsg.put("contentLength", sectionContent.length());
                                    streamHandler.accept(com.alibaba.fastjson.JSON.toJSONString(completeMsg));
                                } catch (Exception e) {
                                    log.error("推送章节完成信号失败, chapterIndex={}", index, e);
                                }
                            }
                            
                        } catch (Exception e) {
                            log.error("章节{}生成异常", index, e);
                            sectionContents.put(index, ""); // 失败时存入空字符串
                        }
                    });
                })
                .toList();
        
        // 等待所有章节生成完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 按章节顺序拼接内容
        StringBuilder fullContent = new StringBuilder();
        for (int i = 0; i < outline.getSections().size(); i++) {
            String sectionContent = sectionContents.get(i);
            if (sectionContent != null && !sectionContent.isEmpty()) {
                if (fullContent.length() > 0) {
                    fullContent.append("\n\n");
                }
                fullContent.append(sectionContent);
            }
        }
        
        String content = fullContent.toString();
        
        log.info("ContentGeneratorAgent 执行完成: 正文长度={}", content.length());

        // 发送 AGENT3_COMPLETE 消息，通知前端正文生成完成
        if (streamHandler != null) {
            try {
                streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
                log.info("已发送 AGENT3_COMPLETE 消息");
            } catch (Exception e) {
                log.error("发送 AGENT3_COMPLETE 消息失败", e);
            }
        }

        return Map.of(OUTPUT_CONTENT, content);
    }

    /**
     * 调用 LLM（流式输出，支持章节标识）
     *
     * @param prompt 提示词
     * @param streamHandler 流式处理器
     * @param chapterIndex 章节索引(从0开始)
     * @return 生成的内容
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler, int chapterIndex) {
        StringBuilder contentBuilder = new StringBuilder();

        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                .doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        // 带章节索引发送流式消息,格式: "AGENT3_STREAMING:章节索引:内容"
                        if (streamHandler != null) {
                            String message = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix() 
                                           + chapterIndex + ":" 
                                           + chunk;
                            streamHandler.accept(message);
                        }
                    }
                })
                .doOnError(error -> log.error("ContentGeneratorAgent 流式调用失败, chapterIndex={}", chapterIndex, error))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 根据风格获取对应的 Prompt 附加内容
     */
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
}

