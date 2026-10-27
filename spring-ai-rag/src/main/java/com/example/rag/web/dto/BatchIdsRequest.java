package com.example.rag.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量删除等“只传 ID”的操作请求。
 * 单次最多 100 项，避免一条请求长时间占用数据库连接。
 */
public record BatchIdsRequest(
        @NotEmpty(message = "请选择至少一项")
        @Size(max = 100, message = "单次最多操作 100 项")
        List<@NotNull(message = "ID 不能为空") Long> ids
) {
}
