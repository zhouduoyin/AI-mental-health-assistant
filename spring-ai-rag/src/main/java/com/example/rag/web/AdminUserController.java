package com.example.rag.web;

import com.example.rag.domain.UserAccount;
import com.example.rag.service.AdminBatchService;
import com.example.rag.service.AuthTokenService;
import com.example.rag.service.UserService;
import com.example.rag.web.dto.AdminUserView;
import com.example.rag.web.dto.BatchIdsRequest;
import com.example.rag.web.dto.BatchResult;
import com.example.rag.web.dto.BatchUserStatusRequest;
import com.example.rag.web.dto.UserCreateRequest;
import com.example.rag.web.dto.UserStatusRequest;
import com.example.rag.web.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@io.swagger.v3.oas.annotations.tags.Tag(name = "后台用户管理", description = "账号增删改查、启停用与批量操作，需要后台账号")
public class AdminUserController {

    private final UserService userService;
    private final AdminBatchService adminBatchService;
    private final AuthTokenService tokenService;

    public AdminUserController(UserService userService,
                               AdminBatchService adminBatchService,
                               AuthTokenService tokenService) {
        this.userService = userService;
        this.adminBatchService = adminBatchService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public List<AdminUserView> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String role) {
        tokenService.requireBack(authorization);
        return userService.list(keyword, role);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @Valid @RequestBody UserCreateRequest request) {
        tokenService.requireBack(authorization);
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public AdminUserView update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable Long id,
                                @Valid @RequestBody UserUpdateRequest request) {
        UserAccount operator = tokenService.requireBack(authorization);
        return userService.update(id, request, operator, authorization);
    }

    @PostMapping("/{id}/status")
    public AdminUserView changeStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable Long id,
                                      @Valid @RequestBody UserStatusRequest request) {
        UserAccount operator = tokenService.requireBack(authorization);
        return userService.changeStatus(id, request.enabled(), operator);
    }

    @PostMapping("/batch/status")
    @Operation(summary = "批量启用 / 停用账号",
            description = "逐项执行；不能停用自己，受保护的账号会出现在 failed 明细里")
    public BatchResult batchStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody BatchUserStatusRequest request) {
        UserAccount operator = tokenService.requireBack(authorization);
        return adminBatchService.changeUserStatus(request.ids(), request.enabled(), operator);
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "批量删除账号",
            description = "逐项执行；不能删除自己，也不能删除最后一个可用后台管理员")
    public BatchResult batchDelete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody BatchIdsRequest request) {
        UserAccount operator = tokenService.requireBack(authorization);
        return adminBatchService.deleteUsers(request.ids(), operator);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        UserAccount operator = tokenService.requireBack(authorization);
        userService.delete(id, operator);
    }
}
