package com.example.rag.web.dto;

/**
 * 批量操作中单项的结果。失败时 message 为业务异常信息，便于前端展示。
 */
public record BatchItemResult(Long id, boolean success, String message) {

    public static BatchItemResult ok(Long id) {
        return new BatchItemResult(id, true, "成功");
    }

    public static BatchItemResult failed(Long id, String message) {
        return new BatchItemResult(id, false, message == null || message.isBlank() ? "操作失败" : message);
    }
}
