package com.kanade.aipassage.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体执行日志表 实体类。
 *
 * @author kanade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "agent_log",camelToUnderline = false)
public class AgentLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务ID
     */
    @Column("taskId")
    private String taskId;

    /**
     * 智能体名称
     */
    @Column("agentName")
    private String agentName;

    /**
     * 开始时间
     */
    @Column("startTime")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Column("endTime")
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    @Column("durationMs")
    private Integer durationMs;

    /**
     * 状态：SUCCESS/FAILED
     */
    private String status;

    /**
     * 错误信息
     */
    @Column("errorMessage")
    private String errorMessage;

    /**
     * 使用的Prompt
     */
    private String prompt;

    /**
     * 输入数据（JSON格式）
     */
    @Column("inputData")
    private String inputData;

    /**
     * 输出数据（JSON格式）
     */
    @Column("outputData")
    private String outputData;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete",isLogicDelete = true)
    private Integer isDelete;

}
