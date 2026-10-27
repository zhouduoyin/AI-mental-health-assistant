package com.example.rag.web;

import com.example.rag.service.AuthTokenService;
import com.example.rag.service.DashboardService;
import com.example.rag.web.dto.DashboardView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "后台仪表盘", description = "由真实业务数据聚合的统计指标，需要后台账号")
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final AuthTokenService tokenService;

    public AdminDashboardController(DashboardService dashboardService, AuthTokenService tokenService) {
        this.dashboardService = dashboardService;
        this.tokenService = tokenService;
    }

    @GetMapping
    @Operation(summary = "仪表盘数据")
    public DashboardView dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        tokenService.requireBack(authorization);
        return dashboardService.build();
    }
}
