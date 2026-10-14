package com.kanade.aipassage.agent.agents;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.kanade.aipassage.agent.context.StreamHandlerContext;
import com.kanade.aipassage.constant.PromptConstant;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewerAgent implements NodeAction {

    private final DeepSeekChatModel chatModel;

    public static final String INPUT_CONTENT = "content";
    public static final String OUTPUT_CONTENT = "content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String content = state.value(INPUT_CONTENT)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少输入内容参数"));
        // log.info("content" + content);
        // todo 特效待设计
        String prompt = PromptConstant.REVIEWER_PROMPT.replace("{content}", content);

        Consumer<String> consumer = StreamHandlerContext.get();
        Flux<ChatResponse> stream = chatModel.stream(new Prompt(prompt));
        StringBuilder stringBuilder = new StringBuilder();
        stream.doOnNext(response -> {
            String chunk = response.getResult().getOutput().getText();
            //log.info("chunk" + chunk);
            if (chunk != null && !chunk.isEmpty()) {
                stringBuilder.append(chunk);
                if (consumer != null) {
                    String message = SseMessageTypeEnum.REVIEWER_STREAMING.getStreamingPrefix()
                            +":"
                            + chunk;
                    consumer.accept(message);
                }
            }
        })
        .doOnError(error -> log.error("ContentGeneratorAgent 流式调用失败", error))
        .blockLast();
        return Map.of(OUTPUT_CONTENT, stringBuilder.toString());
    }
}
