package com.example.rag.web;

import com.example.rag.service.AuthTokenService;
import com.example.rag.service.ConsultationService;
import com.example.rag.web.dto.ConsultationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/consultations")
@Tag(name = "后台咨询记录", description = "咨询会话列表、详情与跟进，需要后台账号")
public class AdminConsultationController {

    private final ConsultationService consultationService;
    private final AuthTokenService tokenService;

    public AdminConsultationController(ConsultationService consultationService,
                                       AuthTokenService tokenService) {
        this.consultationService = consultationService;
        this.tokenService = tokenService;
    }

    @GetMapping
    @Operation(summary = "咨询记录列表")
    public List<ConsultationView> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        tokenService.requireBack(authorization);
        return consultationService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "咨询记录详情")
    public ConsultationView get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable String id) {
        tokenService.requireBack(authorization);
        return consultationService.get(id);
    }

    @PostMapping("/{id}/follow-up")
    @Operation(summary = "标记为已跟进（状态置为已结束）")
    public ConsultationView followUp(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable String id) {
        tokenService.requireBack(authorization);
        return consultationService.followUp(id);
    }
}
