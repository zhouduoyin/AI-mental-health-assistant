package com.example.rag.web.dto;

import java.util.List;

/**
 * 批量操作汇总。部分成功仍返回 200，前端根据 failed 和 items 展示详情。
 */
public record BatchResult(int requested, int succeeded, int failed, List<BatchItemResult> items) {

    public static BatchResult of(List<BatchItemResult> items) {
        List<BatchItemResult> safeItems = List.copyOf(items);
        int succeeded = (int) safeItems.stream().filter(BatchItemResult::success).count();
        return new BatchResult(safeItems.size(), succeeded, safeItems.size() - succeeded, safeItems);
    }
}
