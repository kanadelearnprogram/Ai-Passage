package com.kanade.aipassage.controller;


import com.kanade.aipassage.annotation.AuthCheck;
import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.constant.UserConstant;
import com.kanade.aipassage.model.vo.StatisticsVO;
import com.kanade.aipassage.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics")
@Slf4j
@Tag(name = "StatisticsController", description = "统计分析接口")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    /**
     * 获取系统统计数据（仅管理员）
     */
    @GetMapping("/overview")
    @Operation(summary = "获取系统统计数据")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StatisticsVO> getStatistics() {
        StatisticsVO statistics = statisticsService.getStatistics();
        return ResultUtils.success(statistics);
    }
}

