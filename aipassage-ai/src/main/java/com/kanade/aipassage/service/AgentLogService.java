package com.kanade.aipassage.service;

import com.kanade.aipassage.model.vo.AgentExecutionStats;
import com.mybatisflex.core.service.IService;
import com.kanade.aipassage.model.entity.AgentLog;

/**
 * 智能体执行日志表 服务层。
 *
 * @author kanade
 */
public interface AgentLogService extends IService<AgentLog> {

    void saveLogAsync(AgentLog agentLog);

    AgentExecutionStats getExecutionStats(String taskId);
}
