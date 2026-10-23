package com.kanade.aipassage.aop;

import com.kanade.aipassage.annotation.AgentExecution;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.entity.AgentLog;
import com.kanade.aipassage.service.AgentLogService;
import com.kanade.aipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class AgentExecutionAspect  {

    @Resource
    private AgentLogService agentLogService;

    @Around("@annotation(agentExecution)")
    public Object aroundAgentExecution(ProceedingJoinPoint pij, AgentExecution agentExecution) throws Throwable {
        long start = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();

        String taskId = extractTaskId(pij);
        String inputData = extractInputData(pij);
        String prompt = extractPrompt(pij);

        AgentLog agentLog = AgentLog.builder().taskId(taskId)
                .agentName(agentExecution.value())
                .startTime(startTime)
                .status("RINNING")
                .prompt(prompt)
                .inputData(inputData)
                .build();

        Object result =null;
        try {
            result = pij.proceed();

            agentLog.setStatus("SUCCESS");
            agentLog.setEndTime(LocalDateTime.now());
            agentLog.setDurationMs((int)(System.currentTimeMillis() - start));
            agentLog.setOutputData(extractOutputData(result));
            log.info("智能体执行成功: {}, taskId={}, 耗时={}ms",
                    agentExecution.value(), taskId, agentLog.getDurationMs());

        } catch (Throwable e) {
            // 记录失败状态
            agentLog.setStatus("FAILED");
            agentLog.setEndTime(LocalDateTime.now());
            agentLog.setDurationMs((int) (System.currentTimeMillis() - start));
            agentLog.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getName());

            log.error("智能体执行失败: {}, taskId={}, 错误={}",
                    agentExecution.value(), taskId, e.getMessage(), e);

            throw e;
        }finally {
            // 异步保存日志
            agentLogService.saveLogAsync(agentLog);
        }
        return result;
    }
    private String extractTaskId(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return "unknown";
        }

        // 优先从 ArticleState 中获取
        for (Object arg : args) {
            if (arg instanceof ArticleState) {
                return ((ArticleState) arg).getTaskId();
            }
        }

        // 尝试从第一个 String 参数获取（可能是 taskId）
        for (Object arg : args) {
            if (arg instanceof String) {
                return (String) arg;
            }
        }

        return "unknown";
    }

    private String extractInputData(ProceedingJoinPoint pjp) {
        try {
            Object[] args = pjp.getArgs();
            if (args == null || args.length == 0) {
                return null;
            }

            Map<String, Object> inputMap = new HashMap<>();
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = signature.getParameterNames();

            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                Object arg = args[i];
                // 只记录基本类型和简单对象，避免数据过大
                if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
                    inputMap.put(paramNames[i], arg);
                } else if (arg instanceof ArticleState) {
                    ArticleState state = (ArticleState) arg;
                    inputMap.put("taskId", state.getTaskId());
                    if (state.getTitle() != null) {
                        inputMap.put("mainTitle", state.getTitle().getMainTitle());
                    }
                }
            }

            return inputMap.isEmpty() ? null : GsonUtils.toJson(inputMap);
        } catch (Exception e) {
            log.warn("提取输入数据失败", e);
            return null;
        }
    }
    private String extractOutputData(Object result) {
        try {
            if (result == null) {
                return null;
            }

            // 只记录简单类型，避免数据过大
            if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                return String.valueOf(result);
            }

            // 对于集合类型，只记录数量
            if (result instanceof java.util.List) {
                return "{\"listSize\": " + ((java.util.List<?>) result).size() + "}";
            }

            return "{\"type\": \"" + result.getClass().getSimpleName() + "\"}";
        } catch (Exception e) {
            log.warn("提取输出数据失败", e);
            return null;
        }
    }
    private String extractPrompt(ProceedingJoinPoint pjp) {
        try {
            // 可以根据方法名称推断使用的 Prompt
            // 或从参数中提取，这里简化处理
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        } catch (Exception e) {
            return null;
        }
    }
}
