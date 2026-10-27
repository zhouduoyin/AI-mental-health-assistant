package com.example.rag.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量启停账号。ids 会在服务端去重；单个 ID 失败不会回滚其它项。
 */
public record BatchUserStatusRequest(
        @NotEmpty(message = "请选择至少一个账号")
        @Size(max = 100, message = "单次最多操作 100 个账号")
        List<@NotNull(message = "账号 ID 不能为空") Long> ids,

        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
